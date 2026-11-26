"""検証済み合成 Stack の設定・認証・DB 手順を Linux CI の専用資源へ接続する。"""

import base64
import contextlib
import ipaddress
import json
import os
from pathlib import Path
import selectors
import shutil
import socket
import socketserver
import subprocess
import threading
import time

import mac_runtime as previous


class LinuxStack(previous.Stack):
    """実ユーザー資格を使わず、所有ラベル付き internal network 一つだけを作る。"""

    def __init__(self, work, run_id, images, owner, memory_mib):
        super().__init__(work, run_id, images['python'])
        self.images = images
        self.owner = owner
        self.memory_mib = memory_mib
        self.relay = None
        self.relay_thread = None

    def docker(self, *args, data=None, environment=None, timeout=30, expected=(0,)):
        """CI の既知 daemon だけを使い、Mac ソケット・保存済み CLI 認証を使わない。"""
        call = subprocess.run(['docker', '--config', str(self.config), '--host', 'unix:///var/run/docker.sock',
                               *map(str, args)], input=data, capture_output=True,
                              env=self.env if environment is None else environment,
                              timeout=timeout, check=False)
        if call.returncode not in expected:
            self.docker_diagnostic = {'operation': str(args[0]), 'exitCode': call.returncode,
                                      'lines': self.diagnostic_lines(call.stderr)}
            raise RuntimeError('CAPACITY_DOCKER_' + str(args[0]).upper().replace('-', '_'))
        return call

    def start(self):
        """取得済み immutable image ID と専用ネットワークの所有者を固定する。"""
        previous.check_ports()
        previous.JAVA_IMAGE = self.images['java']
        previous.MYSQL_IMAGE = self.images['mysql']
        for image in self.images.values():
            detail = json.loads(self.docker('image', 'inspect', image).stdout)[0]
            if detail['Id'] != image or detail['Os'] != 'linux' or detail['Architecture'] != 'amd64':
                raise RuntimeError('CAPACITY_IMAGE_ID')
        self.network = self.docker('network', 'create', '--internal',
                                  '--label', 'translacat.docqa.run=' + self.run_id,
                                  '--label', 'translacat.document.capacity.owner=' + self.owner,
                                  self.prefix + '-network').stdout.decode().strip()
        row = json.loads(self.docker('network', 'inspect', self.network).stdout)[0]
        if not row['Internal'] or row['Labels'].get('translacat.document.capacity.owner') != self.owner:
            raise RuntimeError('CAPACITY_NETWORK_NOT_INTERNAL')

    def start_container(self, role, image, flags, command, *, network=None):
        """既存 Stack の create-before-start 記録を維持し、実行所有ラベルを加える。"""
        flags = ['--label', 'translacat.document.capacity.owner=' + self.owner, *flags]
        return super().start_container(role, image, flags, command, network=network)

    def prepare_be(self, source, jar):
        """既存の合成値だけを作り、文書の同時性と予約容量を 1/1/1/1 に固定する。"""
        super().prepare_be(source, jar)
        text = self.be_settings.read_text().replace(
            'translacat.document-translation.temporary-directory=/docwork/jobs',
            'translacat.document-translation.temporary-directory=/run/translacat-document-translation/work')
        for key, value in {'uploads': 1, 'downloads': 1, 'workers': 1,
                           'workspaces': 1, 'workspace-bytes': 20971520}.items():
            text += f'translacat.document-translation.capacity.{key}={value}\n'
        self.be_settings.write_text(text)

    def start_be(self):
        """実 bootJar を noswap tmpfs と swap.max=0 の cgroup で起動する。"""
        self.start_container('be', self.images['java'], [
            '--memory', str(self.memory_mib) + 'm', '--memory-swap', str(self.memory_mib) + 'm',
            '--cpus', '2', '--pids-limit', '128', '--read-only', '--cap-drop', 'ALL',
            '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,noswap,size=64m,mode=1777',
            '--tmpfs', '/run/translacat-document-translation:rw,noexec,nosuid,nodev,noswap,size=32m,mode=0700',
            '--mount', f'type=bind,source={self.job_jar},target=/review.jar,readonly',
            '--mount', f'type=bind,source={self.dictionary},target=/system_full.dic,readonly',
            '--mount', f'type=bind,source={self.be_settings},target=/review.properties,readonly',
            '--mount', f'type=bind,source={self.be_logback},target=/qa-logback.xml,readonly',
            '--entrypoint', '/opt/java/openjdk/bin/java',
        ], ['-Xmx' + str(min(1300, self.memory_mib - 192)) + 'm', '-XX:ActiveProcessorCount=2',
            '-Dlogging.config=file:/qa-logback.xml', '-jar', '/review.jar',
            '--spring.config.location=classpath:/application.properties,file:/review.properties'])
        row = json.loads(self.docker('inspect', self.containers['be']).stdout)[0]
        network = row['NetworkSettings']['Networks']
        if len(network) != 1:
            raise RuntimeError('CAPACITY_BE_NETWORKS')
        self.be_ip = next(iter(network.values()))['IPAddress']
        if not ipaddress.ip_address(self.be_ip).is_private:
            raise RuntimeError('CAPACITY_BE_ADDRESS')

    def start_host_relay(self):
        """host の loopback 一点だけから専用内部 BE へ本文を記録せず中継する。"""
        target = (self.be_ip, previous.BE_PORT)

        class Relay(socketserver.BaseRequestHandler):
            """同じ隔離 BE の TCP ストリームを有限バッファで転送する。"""

            def handle(self):
                """双方の半閉鎖を維持し、外部 host や任意の接続先を受け付けない。"""
                try:
                    with socket.create_connection(target, timeout=4) as upstream:
                        upstream.settimeout(65)
                        self.request.settimeout(65)
                        with selectors.DefaultSelector() as ready:
                            ready.register(self.request, selectors.EVENT_READ, upstream)
                            ready.register(upstream, selectors.EVENT_READ, self.request)
                            while ready.get_map():
                                events = ready.select(timeout=65)
                                if not events:
                                    return
                                for key, _mask in events:
                                    chunk = key.fileobj.recv(65536)
                                    if chunk:
                                        key.data.sendall(chunk)
                                    else:
                                        ready.unregister(key.fileobj)
                                        with contextlib.suppress(OSError):
                                            key.data.shutdown(socket.SHUT_WR)
                except OSError:
                    return

        self.relay = socketserver.ThreadingTCPServer(('127.0.0.1', previous.BE_PORT), Relay)
        self.relay.daemon_threads = True
        self.relay_thread = threading.Thread(target=self.relay.serve_forever, daemon=True)
        self.relay_thread.start()

    def workspace_entries(self):
        """同じ BE の専用領域の相対名だけを読み、本文を扱わない。"""
        result = self.docker('exec', self.containers['be'], 'find',
                             '/run/translacat-document-translation/work', '-maxdepth', '2', '-printf', '%P\n')
        return {line for line in result.stdout.decode().splitlines() if line}

    def close(self):
        """host relay を終了し、既存の所有 ID/label 検証付き清掃を呼ぶ。"""
        if self.relay is not None:
            self.relay.shutdown()
            self.relay.server_close()
            self.relay_thread.join(timeout=5)
            self.relay = None
        return super().close()
