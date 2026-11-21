const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const { setTimeout: delay } = require("node:timers/promises");

const input = process.env.CODEX_DESKTOP_INPUT_DIR;
const results = process.env.CODEX_DESKTOP_RESULTS_DIR;
const container = process.argv[2];

// 같은 UTC 시각으로 조회 기간을 고정하고 SQL을 격리된 MySQL에서 실행한다.
function query(sql) {
    const result = spawnSync("docker", [
        "exec", "-i", container, "mysql", "--batch", "--skip-column-names", "--user=root", "board_acceptance",
    ], {
        input: "SET time_zone='+00:00'; SET timestamp=UNIX_TIMESTAMP('2026-11-21 00:00:00');\n" + sql + "\n",
        encoding: "utf8",
        timeout: 15000,
    });
    if (result.status !== 0) {
        throw new Error(result.stderr?.trim() || String(result.error));
    }

    return result.stdout.trim().split(/\r?\n/).filter(Boolean);
}

// 저장소 쿼리와 합성 fixture의 위치 인자를 SQL 리터럴로 치환한다.
function bind(sql, values) {
    let index = 0;
    const bound = sql.replaceAll("?", () => {
        const value = values[index++];
        return value === null ? "NULL" : typeof value === "string" ? "'" + value.replaceAll("'", "''") + "'" : value;
    });
    assert.equal(index, values.length);
    return bound;
}

// 제품 저장소가 실행하는 목록·인기글·상세·주소 쿼리에서 게시판 조건을 읽는다.
function queries() {
    const source = fs.readFileSync(path.join(input, "BoardRepository.java"), "utf8");
    const directory = fs.readFileSync(path.join(input, "BoardForumRepository.java"), "utf8");
    return {
        select: source.match(/private static final String SELECT = """([\s\S]*?)""";/)[1],
        ranked: source.match(/sql\.append\("""([\s\S]*?)"""\);/)[1],
        scope: source.match(/sql\.append\("( WHERE e\.board = \?[^"]+)"\)/)[1],
        publicOnly: source.match(/sql\.append\("( AND e\.password_hash IS NULL)"\)/)[1],
        detail: source.match(/\.query\(SELECT \+ "( WHERE e\.id = \?[^"]+)"/)[1],
        address: source.match(/return jdbc\.queryForList\("""([\s\S]*?)"""/)[1],
        directory: directory.match(/visible\.addAll\(jdbc\.query\("""([\s\S]*?)"""/)[1],
    };
}

// 구 스키마에서 게시판을 분리한 뒤 원 데이터와 접근 경계를 검사한다.
async function main() {
    let ready = false;
    for (let attempt = 0; attempt < 50; attempt++) {
        try {
            ready = query("SELECT 1;")[0] === "1";
            if (ready) {
                break;
            }
        } catch {
            await delay(2000);
        }
    }
    assert.equal(ready, true);
    const version = query("SELECT VERSION();")[0];
    assert.match(version, process.env.BOARD_HELPER_ENGINE === "tidb" ? /^8\..*TiDB/i : /^8\.4\./);
    const migration = (name) => fs.readFileSync(path.join(input, "src/main/resources/db/migration", name), "utf8");
    query(migration("20261114_boards.sql") + "\n" + migration("20261121_board_engagement.sql"));
    const user = String.fromCharCode(96) + "user" + String.fromCharCode(96);
    query("CREATE TABLE " + user + " (id BIGINT PRIMARY KEY, username VARCHAR(40)); INSERT INTO " + user + " VALUES (90,'writer');");

    // 같은 유형의 두 분류와 기본 글, 관리자·익명 글, 비밀 댓글 및 첨부를 기존 형식으로 입력한다.
    const uuid = (part) => "f0000000-0000-4000-8000-" + String(part).padStart(12, "0");
    const seeds = [[90,"MEMBER","Questions"],[91,"MEMBER","Feedback"],[92,"ADMIN","Staff"],[93,"ANONYMOUS","Anonymous"]];
    let fixtures = seeds.map(([id,type,name]) => bind("INSERT INTO board_category VALUES (?,?,?);", [uuid(id),type,name])).join("\n");
    const entries = [
        [1,"MEMBER",null,null,90], [2,"MEMBER",null,null,91], [3,"MEMBER",null,null,null],
        [4,"MEMBER",null,"locked",90], [5,"MEMBER",1,"locked reply",null],
        [6,"ADMIN",null,null,92], [7,"ANONYMOUS",null,null,93],
    ];
    for (const [id,type,parent,password,category] of entries) {
        const root = parent === null ? null : uuid(parent);
        fixtures += bind("INSERT INTO board_entry (id,board,author_id,root_id,parent_id,title,content,password_hash,created_at,updated_at) VALUES (?,?,90,?,?,'Title','Body',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));", [uuid(id),type,root,root,password]);
        if (root === null) {
            fixtures += bind("INSERT INTO board_post_meta VALUES (?,?,12);", [uuid(id), category === null ? null : uuid(category)]);
            for (const account of [10, 11, 12]) {
                fixtures += bind("INSERT INTO board_post_view VALUES (?,?,'2026-11-20 00:00:00');", [uuid(id),account]);
                fixtures += bind("INSERT INTO board_post_vote VALUES (?,?,'LIKE');", [uuid(id),account]);
            }
        }
    }
    fixtures += bind("INSERT INTO board_attachment VALUES (?,'MEMBER',90,?,'file.txt',1,33554432,'test','ATTACHED','2026-12-01');", [uuid(20),uuid(1)]);
    fixtures += bind("INSERT INTO board_attachment VALUES (?,'MEMBER',90,NULL,'pending.txt',1,33554432,'test','READY','2026-12-01');", [uuid(21)]);
    query(fixtures);
    if (process.env.BOARD_FORUM_FIXTURE_ONLY === "1") return;
    const change = migration("20261121_board_forums.sql");
    query(change);
    query(bind("INSERT INTO board_forum VALUES (?,'MEMBER','Brand new');", [uuid(94)]));
    query(change);

    // 각 게시판의 일반·인기 목록, 관리자 권한, 이전 URL과 직접 조회 경계를 확인한다.
    const sql = queries();
    const ids = (rows) => rows.map((row) => row.split("\t")[0]);
    const list = (type, forum, popular) => ids(query(bind(
        sql.select + (popular ? sql.ranked : "") + sql.scope + (popular ? sql.publicOnly : "") + " ORDER BY e.id DESC;",
        popular ? [3,3,70,type,forum] : [type,forum])));
    assert.deepEqual(list("MEMBER",uuid(90),false), [uuid(4),uuid(1)]);
    assert.deepEqual(list("MEMBER",uuid(90),true), [uuid(1)]);
    assert.deepEqual(list("MEMBER",uuid(91),true), [uuid(2)]);
    assert.deepEqual(list("MEMBER",null,true), [uuid(3)]);
    assert.deepEqual(list("ADMIN",uuid(92),true), [uuid(6)]);
    assert.deepEqual(list("ANONYMOUS",uuid(93),true), [uuid(7)]);
    assert.deepEqual(list("MEMBER",uuid(94),false), []);
    assert(!ids(query(bind(sql.directory,[0]))).includes(uuid(92)));
    assert(ids(query(bind(sql.directory,[1]))).includes(uuid(92)));
    assert.deepEqual(ids(query(bind(sql.select + sql.detail,[uuid(1),"MEMBER",uuid(91)]))), []);
    assert.deepEqual(ids(query(bind(sql.select + sql.detail,[uuid(1),"MEMBER",uuid(90)]))), [uuid(1)]);
    assert.deepEqual(query(bind(sql.address,[uuid(1),"MEMBER"])), [uuid(90)]);
    assert.deepEqual(query(bind(sql.address,[uuid(6),"MEMBER"])), []);

    // 원 글과 연결된 댓글·첨부 및 집계 값이 보존됐는지 확인한다.
    assert.deepEqual(query(bind("SELECT forum_id,root_id FROM board_entry WHERE id=?;", [uuid(5)])), [uuid(90)+"\t"+uuid(1)]);
    assert.deepEqual(query(bind("SELECT forum_id,entry_id FROM board_attachment WHERE id=?;", [uuid(20)])), [uuid(90)+"\t"+uuid(1)]);
    assert.deepEqual(query(bind("SELECT forum_id FROM board_attachment WHERE id=?;", [uuid(21)])), ["NULL"]);
    assert.deepEqual(query(bind("SELECT view_count FROM board_post_meta WHERE post_id=?;", [uuid(1)])), ["12"]);
    assert.deepEqual(query(bind("SELECT COUNT(*) FROM board_post_vote WHERE post_id=?;", [uuid(1)])), ["3"]);

    const report = { version, migrationAppliedTwice: true, listsAndPopular: "passed", adminAndLegacyLinks: "passed",
        postReplyAttachmentAndCounts: "passed" };
    fs.writeFileSync(path.join(results,"mysql-smoke.json"), JSON.stringify(report,null,2));
    console.log(JSON.stringify(report));
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});

