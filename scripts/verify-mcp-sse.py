#!/usr/bin/env python3
"""Verify a running Tibyaan MCP server end to end, out of process, over the network.

Opens the SSE stream, reads the session endpoint, then drives the JSON-RPC handshake
(initialize, tools/list, tools/call) and prints what the server returns. This is a real
external client using only the Python standard library, so it exercises the network and
transport path an adopter's client would, not the in-process test.

Usage:
    python scripts/verify-mcp-sse.py
    python scripts/verify-mcp-sse.py http://localhost:8080 getProduct '{"id": 5}'

Args (all optional): base URL, a tool name to call, and its arguments as JSON.
With no tool name it stops after tools/list.
"""
import json
import sys
import threading
import time
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
TOOL = sys.argv[2] if len(sys.argv) > 2 else "getProduct"
ARGS = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {"id": 5}

responses = {}
endpoint = {}
lock = threading.Lock()
stop = threading.Event()


def sse_reader():
    req = urllib.request.Request(BASE + "/sse", headers={"Accept": "text/event-stream"})
    resp = urllib.request.urlopen(req, timeout=30)
    event, data_lines = None, []
    while not stop.is_set():
        raw = resp.readline()
        if not raw:
            break
        line = raw.decode("utf-8").rstrip("\r\n")
        if line == "":
            data = "\n".join(data_lines)
            if event == "endpoint":
                endpoint["url"] = data.strip()
            else:
                try:
                    msg = json.loads(data)
                    if isinstance(msg, dict) and "id" in msg:
                        with lock:
                            responses[msg["id"]] = msg
                except ValueError:
                    pass
            event, data_lines = None, []
        elif line.startswith("event:"):
            event = line[6:].strip()
        elif line.startswith("data:"):
            data_lines.append(line[5:].lstrip())


def post(body):
    url = endpoint["url"]
    if url.startswith("/"):
        url = BASE + url
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    urllib.request.urlopen(req, timeout=10)


def wait_for(rid, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        with lock:
            if rid in responses:
                return responses[rid]
        time.sleep(0.05)
    return None


def main():
    threading.Thread(target=sse_reader, daemon=True).start()
    for _ in range(100):
        if "url" in endpoint:
            break
        time.sleep(0.1)
    if "url" not in endpoint:
        print("FAIL: no SSE endpoint event from", BASE + "/sse")
        sys.exit(1)
    print("SSE endpoint:", endpoint["url"])

    post({"jsonrpc": "2.0", "id": 1, "method": "initialize",
          "params": {"protocolVersion": "2024-11-05", "capabilities": {},
                     "clientInfo": {"name": "tibyaan-verify", "version": "1.0"}}})
    init = wait_for(1)
    if not (init and "result" in init):
        print("FAIL: initialize did not return a result")
        sys.exit(1)
    print("initialize ok, serverInfo:", json.dumps(init["result"].get("serverInfo", {})))

    post({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}})

    post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
    tools = wait_for(2)
    names = [t["name"] for t in tools["result"]["tools"]] if tools and "result" in tools else []
    print("tools/list:", names)

    if TOOL:
        post({"jsonrpc": "2.0", "id": 3, "method": "tools/call",
              "params": {"name": TOOL, "arguments": ARGS}})
        call = wait_for(3)
        if call and "result" in call:
            print("tools/call %s%s:" % (TOOL, json.dumps(ARGS)), json.dumps(call["result"]))
        else:
            print("FAIL: tools/call", TOOL, "returned", json.dumps(call) if call else "TIMEOUT")
            stop.set()
            sys.exit(1)

    stop.set()


if __name__ == "__main__":
    main()
