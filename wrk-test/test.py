#!/usr/bin/env python3

import asyncio
import os
import signal
import sys
import random

async def start_server():
    return await asyncio.create_subprocess_exec("./gradlew", "-q", "--console=plain", "run", stdout=asyncio.subprocess.DEVNULL)

async def check_returncode(cmd, proc, stderr):
    if proc.returncode != 0:
        print(f"Error status {proc.returncode} from command `{cmd}`:\n{stderr.decode().rstrip()}", file=sys.stderr)
        return False
    return True

async def check_stderr(cmd, proc, stderr):
    stderr = stderr.decode().rstrip()
    if len(stderr) > 0:
        print(f"Error status {proc.returncode} from command `{cmd}`:\n{stderr}", file=sys.stderr)
        return False
    return True

async def run_test(account_id):
    await asyncio.sleep(random.random()*0.1) # randomize startup order
    reports = []
    for trial in range(0, 4):
        cmd = ["wrk", "-t1", "-c100", "-d1000s", "http://localhost:8080/"]
        if account_id == "invalidN@example.com":
            cmd += ["-s", "invalidN.lua"]
        else:
            cmd += ["-s", "report.lua"]
        if account_id != "ANONYMOUS":
            cmd += ["-H", "X-Account-ID: " + account_id]
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        stdout, stderr = await proc.communicate()
        if await check_returncode(cmd, proc, stderr):
            lines = stdout.splitlines()
            lines = lines[1 + lines.index(b'------------------------------'):]
            rows = [['account ID', account_id], ['trial', str(trial)]] + list(map(lambda l: l.decode().split('\t'), lines))
            reports.append((list(map(lambda r: r[0], rows)), list(map(lambda r: r[1], rows))))
    return reports

async def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(os.path.join(script_dir, ".."))
    server = await start_server()
    os.chdir(script_dir)
    try:
        print("started server", file=sys.stderr)
        await asyncio.sleep(2)
        if server.returncode == None:
            test_reports = await asyncio.gather(
                run_test("invalid@example.com"),
                run_test("invalidN@example.com"),
                run_test("ANONYMOUS"),
                run_test("alice@example.com"),
                run_test("bob@example.com"),
                run_test("carol@example.com"),
                run_test("chuck@example.com"),
                run_test("craig@example.com"),
                run_test("dan@example.com"),
                run_test("erin@example.com"),
                run_test("eve@example.com"),
                run_test("admin@example.com")
            )
            print("finished test", file=sys.stderr)
            header = test_reports[0][0][0]
            print("\t".join(header))
            for reports in test_reports:
                for report in reports:
                    assert(report[0] == header)
                    print("\t".join(report[1]))
    finally:
        if server.returncode != None:
            print("server exited prematurely", file=sys.stderr)
            sys.exit(1)
        server.terminate()
        print("stopped server", file=sys.stderr)
        await server.wait()
        print("server exited", file=sys.stderr)

if __name__ == "__main__":
    asyncio.run(main())
