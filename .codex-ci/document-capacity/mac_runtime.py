"""Mac Mini 上で V2 製品とローカル合成応答を隔離起動する。"""
from __future__ import annotations

import base64
import contextlib
import http.client
import json
import os
import re
import secrets
import shutil
import signal
import socket
import subprocess
import time
from pathlib import Path
from zipfile import ZipFile

from fixture_provider import MODEL

BE_PORT = 23181
FE_PORT = 23180
GATEWAY_PORT = 26081
PORTS = (BE_PORT, FE_PORT, GATEWAY_PORT)
ORIGIN = f"http://127.0.0.1:{GATEWAY_PORT}"
DOCKER = Path("/Applications/Docker.app/Contents/Resources/bin/docker")
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
JAVA_IMAGE = "eclipse-temurin@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5"
MYSQL_IMAGE = "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb"
MYSQL_MIGRATIONS = (
    "20261019_auth_sessions.sql",
    "20261122_document_translation_jobs.sql",
    "20261122_document_translation_object_writes.sql",
    "20261123_document_translation_multipart_uploads.sql",
    "20261122_image_translation.sql",
    "20261122_camera_ocr_jobs.sql",
    "20261122_wordbook.sql",
    "20261004_novel_embedded.sql",
    "20261017_novel_target_language.sql",
    "20261017_novel_translation_shared_cache.sql",
    "20261018_novel_toc_translation.sql",
    "20261023_novel_account_deletion.sql",
)


# Docker とビルドに継承させる一般環境変数だけを選ぶ。
def isolated_environment() -> dict[str, str]:
    keep = ("PATH", "HOME", "USER", "LOGNAME", "LANG", "LC_CTYPE", "TMPDIR", "CODEX_MAC_TOOLS_DIR")
    return {name: os.environ[name] for name in keep if name in os.environ}


# 予約するホストポートが既存プロセスに使われていれば作業を始めない。
def check_ports() -> None:
    with contextlib.ExitStack() as opened:
        for port in PORTS:
            probe = opened.enter_context(socket.socket(socket.AF_INET, socket.SOCK_STREAM))
            probe.bind(("127.0.0.1", port))


# ループバックだけに接続し、HTTP 本文の最大長を呼び出し元で決める。
def http_call(port, method, path, data=None, headers=None, limit=262144):
    if port not in PORTS or not path.startswith("/") or path.startswith("//"):
        raise ValueError("QA_LOOPBACK_REQUEST")
    with contextlib.closing(http.client.HTTPConnection("127.0.0.1", port, timeout=30)) as connection:
        connection.request(method, path, body=data, headers=headers or {})
        with connection.getresponse() as response:
            payload = response.read(limit + 1)
            if len(payload) > limit:
                raise ValueError("QA_LOOPBACK_LIMIT")
            return response.status, payload


# 認証用の値は Docker 引数と結果に出さず、仕事の保護領域だけに保存する。
def secret_bytes(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)


class Stack:
    """同じ run ID のコンテナ、ネットワーク、ネイティブ子プロセスだけを所有する。"""

    def __init__(self, work: Path, run_id, provider_image: str):
        """新しい仕事領域を初期化する。Docker オブジェクトは start まで作らない。"""
        self.work = work
        self.run_id = str(run_id)
        self.prefix = "docqa-v2-" + run_id.hex[:14]
        self.provider_image = provider_image
        self.containers = {}
        self.children = []
        self.network = None
        self.bridge = None
        self.env = isolated_environment()
        self.config = work / "docker-client"
        self.config.mkdir(mode=0o700)
        self.db_root = secrets.token_hex(24)
        self.db_user_password = secrets.token_hex(24)
        self.provider_token = secrets.token_urlsafe(40)
        self.novel_token = secrets.token_urlsafe(40)
        self.job_jar = None
        self.be_settings = None
        self.docker_diagnostic = None

    # 既存ユーザーの CLI 設定を読み込まず、公開イメージの実行時ダウンロードも行わない。
    def docker(self, *args, data=None, environment=None, timeout=30, expected=(0,)):
        env = self.env if environment is None else environment
        call = subprocess.run(
            [str(DOCKER), "--config", str(self.config), "--host",
             f"unix://{Path.home()}/.docker/run/docker.sock", *map(str, args)],
            input=data, capture_output=True, env=env, timeout=timeout, check=False,
        )
        if call.returncode not in expected:
            self.docker_diagnostic = {
                "operation": str(args[0]), "exit_code": call.returncode,
                "lines": self.diagnostic_lines(call.stderr),
            }
            raise RuntimeError(f"QA_DOCKER_{str(args[0]).upper().replace('-', '_')}")
        return call

    # Docker のブートエラーは固定環境だけで扱い、合成シークレットも結果から落とす。
    def diagnostic_lines(self, data):
        raw = data[-32768:].decode("utf-8", errors="replace")
        for value in (self.db_root, self.db_user_password, self.provider_token, self.novel_token):
            raw = raw.replace(value, "[redacted]")
        private = re.compile(r"token|credential|secret|password|api.?key|authorization", re.I)
        return [line[:380] for line in raw.splitlines() if line.strip() and not private.search(line)][-12:]

    # ドキュメントを一件も扱っていない boot 段階のエラーだけを集計する。
    def boot_errors(self):
        evidence = {}
        for role, identifier in self.containers.items():
            with contextlib.suppress(RuntimeError, OSError, subprocess.TimeoutExpired):
                result = self.docker("logs", "--tail", "150", identifier)
                combined = result.stderr + b"\n" + result.stdout
                raw = combined[-65536:].decode("utf-8", errors="replace")
                for value in (self.db_root, self.db_user_password, self.provider_token, self.novel_token):
                    raw = raw.replace(value, "[redacted]")
                raw = re.sub(r"(?i)(password|token|secret|api.?key|authorization)([=:]\s*)[^\s,;]+",
                             r"\1\2[redacted]", raw)
                all_lines = [line[:400] for line in raw.splitlines() if line.strip()]
                errors = [line for line in all_lines if re.search(
                    r"error|exception|failed|caused by|\.properties|description:|action:",
                    line, re.I,
                ) and not line.lstrip().startswith("at ")]
                evidence[role] = {
                    "lines_seen": len(all_lines),
                    "startup": all_lines[:4],
                    "errors": errors[-28:],
                }
        return evidence

    # イメージを固定し、外向き経路を持たない専用ネットワークを作る。
    def start(self) -> None:
        """ローカルの固定 ARM64 イメージを照合してネットワークを所有する。"""
        check_ports()
        for image in (JAVA_IMAGE, MYSQL_IMAGE, self.provider_image):
            observed = self.docker("image", "inspect", "--format", "{{.Os}} {{.Architecture}} {{.Id}}", image)
            if observed.stdout.decode().strip() != "linux arm64 " + image.rsplit("@", 1)[1]:
                raise RuntimeError("QA_DOCKER_IMAGE_ID")
        name = self.prefix + "-network"
        self.network = self.docker(
            "network", "create", "--internal", "--label", f"translacat.docqa.run={self.run_id}", name,
        ).stdout.decode().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", self.network):
            raise RuntimeError("QA_DOCKER_NETWORK_ID")
        self.bridge = self.docker(
            "network", "create", "--label", f"translacat.docqa.run={self.run_id}", self.prefix + "-bridge",
        ).stdout.decode().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", self.bridge):
            raise RuntimeError("QA_DOCKER_BRIDGE_ID")

    # 固定ラベル、予約済みネットワークと装置の制限をすべての子コンテナに適用する。
    def start_container(self, role, image, flags, command, *, network=None):
        identifier = self.docker(
            "create", "--pull=never", "--name", self.prefix + "-" + role,
            "--network", network or self.network, "--network-alias", role, "--label", f"translacat.docqa.run={self.run_id}",
            "--log-driver", "local", "--log-opt", "max-size=4m", "--log-opt", "max-file=1",
            "--log-opt", "compress=false",
            "--security-opt", "no-new-privileges", *flags, image, *command, timeout=60,
        ).stdout.decode().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", identifier):
            raise RuntimeError("QA_DOCKER_CONTAINER_ID")
        # create の成功を先に記録し、start が失敗した場合も同じ ID を片付ける。
        self.containers[role] = identifier
        self.docker("start", identifier, timeout=30)

    # 所有する MySQL のソケットからだけ SQL を実行する。
    def sql(self, sql, *, allowed=(0,), timeout=50):
        if isinstance(sql, str):
            sql = sql.encode()
        env = self.env | {"MYSQL_PWD": self.db_root}
        return self.docker(
            "exec", "-i", "-e", "MYSQL_PWD", self.containers["mysql"],
            "mysql", "--protocol=socket", "--user=root", "--batch", "--skip-column-names", "translacat_review",
            data=sql, environment=env, timeout=timeout, expected=allowed,
        )

    # 空の揮発性データベースだけを起動し、元のマイグレーション順を再現する。
    def start_mysql(self, source):
        """製品スキーマを適用し、過去のジョブが存在しない状態を作る。"""
        path = self.work / "mysql.env"
        lines = [
            f"MYSQL_ROOT_PASSWORD={self.db_root}", "MYSQL_USER=review_app",
            f"MYSQL_PASSWORD={self.db_user_password}", "MYSQL_DATABASE=translacat_review",
        ]
        secret_bytes(path, ("\n".join(lines) + "\n").encode())
        self.start_container("mysql", MYSQL_IMAGE, [
            "--env-file", str(path), "--memory", "1200m", "--cpus", "1.5",
            "--tmpfs", "/var/lib/mysql:rw,nosuid,nodev,size=600m",
        ], ["--innodb-buffer-pool-size=128M", "--skip-log-bin", "--max-connections=24", "--mysqlx=OFF"])
        path.unlink()
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            # 初期設定用の Unix socket 接続だけでは本体が起動したと判断しない。
            ready = self.docker(
                "exec", "-e", "MYSQL_PWD", self.containers["mysql"], "mysql",
                "--protocol=tcp", "--host=127.0.0.1", "--user=review_app",
                "--connect-timeout=2", "--execute=SELECT 1", "translacat_review",
                environment=self.env | {"MYSQL_PWD": self.db_user_password}, timeout=4, expected=(0, 1),
            )
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("QA_MYSQL_READY")
        self.sql("CREATE TABLE `user` (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB;")
        for name in MYSQL_MIGRATIONS:
            self.sql((source / "be/scripts/sql" / name).read_bytes())
        if self.sql("SELECT COUNT(*) FROM document_translation_job;").stdout.strip() != b"0":
            raise RuntimeError("QA_MYSQL_NOT_EMPTY")

    # 代替モデルはコンテナ間の専用ネットワークでだけ待ち受ける。
    def start_provider(self, script):
        """プロバイダー資格情報を参照せず、合成応答を開始する。"""
        path = self.work / "provider.env"
        secret_bytes(path, (
            f"TRANSLACAT_QA_FIXTURE_TOKEN={self.provider_token}\n"
            "TRANSLACAT_QA_FIXTURE_BIND=0.0.0.0\nPYTHONDONTWRITEBYTECODE=1\n"
        ).encode())
        self.start_container("fixture", self.provider_image, [
            "--env-file", str(path), "--memory", "128m", "--cpus", "0.5", "--read-only",
            "--cap-drop", "ALL", "--mount", f"type=bind,source={script},target=/fixture.py,readonly",
        ], ["python", "-B", "/fixture.py"])
        path.unlink()

    # 生の文書を置くパスだけは、ゲスト内で個別にマウントした tmpfs を使う。
    def prepare_be(self, source, jar: Path):
        """検証専用設定を作り、マウントする製品 jar と辞書を指定する。"""
        self.job_jar = jar
        self.dictionary = source / "be/src/main/resources/system_full.dic"
        if self.dictionary.stat().st_size != 359831203:
            raise RuntimeError("QA_BE_DICTIONARY")
        random_b64 = lambda size=32: base64.b64encode(secrets.token_bytes(size)).decode()
        disabled = "http://127.0.0.1:9"
        config = {
            "server.address": "0.0.0.0", "server.port": str(BE_PORT),
            "server.tomcat.connection-timeout": "30s",
            "spring.datasource.url": "jdbc:mysql://mysql:3306/translacat_review"
                "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC",
            "spring.datasource.username": "review_app", "spring.datasource.password": self.db_user_password,
            "spring.datasource.driver-class-name": "com.mysql.cj.jdbc.Driver",
            "spring.datasource.hikari.maximum-pool-size": "6",
            "spring.jpa.hibernate.ddl-auto": "update", "spring.jpa.show-sql": "false",
            "spring.main.banner-mode": "off", "logging.config": "file:/qa-logback.xml",
            "spring.servlet.multipart.max-file-size": "35MB",
            "spring.servlet.multipart.max-request-size": "36MB", "cors.allowed-origin": ORIGIN,
            "jwt.token.secret-key": random_b64(48), "jwt.token.expired.access": "3600000",
            "jwt.token.expired.refresh": "86400000",
            "spring.security.oauth2.client.registration.google.client-id": "local-review.apps.invalid",
            "spring.ai.google.genai.api-key": "local-review-unused-gemini",
            "external.use-proxy": "false", "external.google.proxy-url": disabled,
            "sudachi.dictionary.path": "/system_full.dic", "translacat.auth.email.code-secret": secrets.token_hex(32),
            "account.deletion.enabled": "false", "account.deletion.worker-enabled": "false",
            "ai-server.url": "http://fixture:27182", "ai-server.api-key": self.provider_token,
            "novel.ai.base-url": disabled, "novel.ai.api-key": self.novel_token,
            "novel.glossary.selection-enabled": "false",
            "novel.audio.spool-dir": "/tmp/novel-spool", "translacat.ai.chat-translation.mode": "sync",
            "language-learning.url": disabled, "language-learning.internal-jwt.secret-base64": random_b64(),
            "chat.gateway.base-url": disabled, "chat.gateway.environment": "Development",
            "chat.gateway.secret-base64": random_b64(), "chat.core.identity.environment": "Development",
            "chat.core.identity.secret-base64": random_b64(), "exchange-rate.frankfurter.base-url": disabled,
            "translacat.document-translation.enabled": "true",
            "translacat.document-translation.temporary-directory": "/docwork/jobs",
            "translacat.document-translation.runtime-profile": "volatile",
            "translacat.document-translation.expected-model": MODEL,
            "translacat.board.storage.bucket": "local-qa-unused",
            "translacat.image-translation.enabled": "false",
            "translacat.image-translation.public-origin": ORIGIN,
            "translacat.image-translation.asset-secret": secrets.token_hex(32),
            "translacat.image-translation.local-root": "/tmp/unused-image",
            "camera-translation.ocr-key": random_b64(), "translacat.storage.type": "local",
            "translacat.storage.local.root-path": "/tmp/unused-storage",
        }
        self.be_settings = self.work / "be.properties"
        secret_bytes(self.be_settings, "".join(f"{key}={value}\n" for key, value in config.items()).encode())
        self.be_logback = self.work / "qa-logback.xml"
        secret_bytes(self.be_logback, b"""<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder><pattern>%level %logger{30} - %msg%n%ex{12}</pattern></encoder>
  </appender>
  <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</configuration>
""")

    # BE が自分で workspace を取得する前にコンテナ内の tmpfs を記録する。
    def start_be(self) -> None:
        """封印済みソースから生成した bootJar を Linux の一時領域で起動する。"""
        if self.job_jar is None or self.be_settings is None:
            raise RuntimeError("QA_BE_CONFIGURATION")
        self.start_container("be", JAVA_IMAGE, [
            "--memory", "2300m", "--cpus", "2.5", "--read-only", "--cap-drop", "ALL",
            "--tmpfs", "/tmp:rw,nosuid,nodev,size=128m",
            "--tmpfs", "/docwork:rw,noexec,nosuid,nodev,size=160m",
            "--mount", f"type=bind,source={self.job_jar},target=/review.jar,readonly",
            "--mount", f"type=bind,source={self.dictionary},target=/system_full.dic,readonly",
            "--mount", f"type=bind,source={self.be_settings},target=/review.properties,readonly",
            "--mount", f"type=bind,source={self.be_logback},target=/qa-logback.xml,readonly",
            "--entrypoint", "/opt/java/openjdk/bin/java",
        ], [
            "-Xmx1300m", "-XX:ActiveProcessorCount=3", "-Dlogging.config=file:/qa-logback.xml",
            "-jar", "/review.jar",
            "--spring.config.location=classpath:/application.properties,file:/review.properties",
        ])
        observed = self.docker("exec", self.containers["be"], "stat", "-f", "-c", "%T", "/docwork")
        if observed.stdout.strip() != b"tmpfs":
            raise RuntimeError("QA_BE_TMPFS")
    # 通常ブリッジと内部ネットワークの二本を使い、BE 自体は外へ接続しない。
    def start_relay(self, script):
        """Mac ループバックだけに BE の固定ポートを公開する。"""
        self.start_container("relay", self.provider_image, [
            "--publish", f"127.0.0.1:{BE_PORT}:{BE_PORT}", "--memory", "96m", "--cpus", "0.4",
            "--read-only", "--cap-drop", "ALL", "--mount", f"type=bind,source={script},target=/relay.py,readonly",
        ], ["python", "-B", "/relay.py"], network=self.bridge)
        self.docker("network", "connect", self.network, self.containers["relay"])

    # 固定した HTTP 状態が返るまで期限付きで待つ。
    def wait_http(self, port, path, statuses, seconds, *, method="GET", body=None, headers=None) -> None:
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if port == BE_PORT and "be" in self.containers:
                state = self.docker("inspect", "--format", "{{.State.Running}}", self.containers["be"])
                if state.stdout.strip() != b"true":
                    raise RuntimeError("QA_BE_EXITED_BEFORE_READY")
            try:
                status, _ = http_call(port, method, path, body, headers, limit=1048576)
            except (OSError, http.client.HTTPException):
                status = None
            if status in statuses:
                return
            time.sleep(0.75)
        raise RuntimeError(f"QA_HTTP_{port}_TIMEOUT")

    def wait_be_ready(self):
        """ログイン API が空の合成 POST を受け取り、入力検証できるまで待つ。"""
        self.wait_http(
            BE_PORT, "/api/v1/auth/login", {400}, 100,
            method="POST", body=b"{}", headers={"Content-Type": "application/json"},
        )

    # 製品の同じ BCrypt ヘルパーを使い、検証用利用者だけを投入する。
    def seed_account(self, java, source, jar, label):
        """一時アカウントを作り、通常の login API が返す合成トークンを返す。"""
        self.crypto = self.work / "bcrypt.jar"
        if not self.crypto.exists():
            with ZipFile(jar) as package:
                crypto = [name for name in package.namelist() if name.startswith("BOOT-INF/lib/spring-security-crypto-")]
                if len(crypto) != 1:
                    raise RuntimeError("QA_BCRYPT_JAR")
                secret_bytes(self.crypto, package.read(crypto[0]))
        login = {
            "email": "review-" + secrets.token_hex(4) + "@translacat.test",
            "password": "QaA7!" + secrets.token_hex(16),
        }
        public_id = "review" + secrets.token_hex(7)
        process = subprocess.run(
            [str(java), "-cp", str(self.crypto), str(source / "server/bootstrap-be-password.java")],
            input=(login["password"] + "\n").encode(), capture_output=True, env=self.env, timeout=25, check=True,
        )
        password_hash = process.stdout.decode().strip()
        if not re.fullmatch(r"\$2[aby]\$12\$[./A-Za-z0-9]{53}", password_hash) or label not in {"Owner", "Other"}:
            raise RuntimeError("QA_BCRYPT_FORMAT")
        self.sql(f"""
START TRANSACTION;
INSERT INTO `user` (
    email,password,username,account_state,social_type,authority,public_id,
    email_verified_at,created_at,updated_at,created_by,updated_by
) VALUES (
    '{login["email"]}','{password_hash}','{label}','ACTIVE','LOCAL','USER','{public_id}',
    UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'REVIEW','REVIEW'
);
INSERT INTO user_profile (user_id,nickname,deleted,created_at,updated_at,created_by,updated_by)
VALUES (LAST_INSERT_ID(),'{label}',FALSE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'REVIEW','REVIEW');
COMMIT;
""")
        login_body = json.dumps(login).encode()
        status, body = http_call(BE_PORT, "POST", "/api/v1/auth/login", login_body, {"Content-Type": "application/json"})
        data = json.loads(body).get("body", {})
        if status != 200 or data.get("role") != "USER" or not isinstance(data.get("accessToken"), str):
            raise RuntimeError("QA_BE_LOGIN")
        return login, data["accessToken"]

    # 固定シェルを使わず、Linux 内で現存する仕事ディレクトリを一覧する。
    def workspace_entries(self):
        """既知のファイル名だけを返す。Office 本文やファイル内容は出力しない。"""
        result = self.docker(
            "exec", self.containers["be"], "find", "/docwork/jobs", "-maxdepth", "2", "-printf", "%P\n",
        )
        return {line for line in result.stdout.decode().splitlines() if line}

    # Docker ID と仕事ラベルが一致するときだけ、対象コンテナを終了する。
    def remove_container(self, role, *, crash=False):
        """所有コンテナを削除する。crash のときは強制終了前の生存を要求する。"""
        identifier = self.containers[role]
        query = "{{.Id}} {{index .Config.Labels \"translacat.docqa.run\"}} {{.State.Running}}"
        receipt = self.docker("inspect", "--format", query, identifier).stdout.decode().split()
        if receipt[:2] != [identifier, self.run_id] or (crash and receipt[2:] != ["true"]):
            raise RuntimeError("QA_CONTAINER_OWNERSHIP")
        if crash:
            self.docker("kill", "--signal", "KILL", identifier)
        self.docker("rm", "--force", identifier, timeout=25)
        del self.containers[role]

    # 合成証跡を残し、所有したプロセスとオブジェクトだけを片付ける。
    def close(self) -> dict:
        """すべての既知のリソースを片付け、個別の成功状態を返す。"""
        complete = True
        for child in reversed(self.children):
            try:
                if child.poll() is None:
                    os.killpg(child.pid, signal.SIGTERM)
                    try:
                        child.wait(timeout=6)
                    except subprocess.TimeoutExpired:
                        os.killpg(child.pid, signal.SIGKILL)
                        child.wait(timeout=4)
            except (OSError, subprocess.TimeoutExpired):
                complete = False
        for role in list(reversed(self.containers)):
            try:
                self.remove_container(role)
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                complete = False
        for field in ("bridge", "network"):
            identifier = getattr(self, field)
            if identifier is None:
                continue
            try:
                value = self.docker("network", "inspect", "--format",
                                    "{{.Id}} {{index .Labels \"translacat.docqa.run\"}}", identifier)
                if value.stdout.decode().split() != [identifier, self.run_id]:
                    raise RuntimeError("QA_NETWORK_OWNERSHIP")
                self.docker("network", "rm", identifier)
                setattr(self, field, None)
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                complete = False
        try:
            label = f"label=translacat.docqa.run={self.run_id}"
            live = self.docker("ps", "-aq", "--filter", label).stdout.strip()
            networks = self.docker("network", "ls", "-q", "--filter", label).stdout.strip()
            complete = complete and not live and not networks
        except (RuntimeError, OSError, subprocess.TimeoutExpired):
            complete = False
        return {"owned_resources_removed": complete and not self.containers and not self.network and not self.bridge}
