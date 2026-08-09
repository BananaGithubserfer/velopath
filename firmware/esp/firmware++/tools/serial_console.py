#!/usr/bin/env python3
import argparse
import queue
import threading
import time

import serial


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Interactive serial console for ESP32-C3 controller."
    )
    parser.add_argument("--port", default="COM5", help="Serial port (default: COM5)")
    parser.add_argument("--baud", type=int, default=115200, help="Baudrate (default: 115200)")
    parser.add_argument(
        "--eol",
        default="LF",
        choices=["LF", "CRLF", "CR"],
        help="Line ending used for sent commands",
    )
    return parser


def reader_loop(ser: serial.Serial, stop_event: threading.Event, lines: queue.Queue) -> None:
    while not stop_event.is_set():
        try:
            raw = ser.readline()
        except serial.SerialException as exc:
            lines.put(f"[serial-error] {exc}")
            stop_event.set()
            return

        if not raw:
            continue

        text = raw.decode("utf-8", errors="replace").rstrip("\r\n")
        if text:
            lines.put(text)


def drain_lines(lines: queue.Queue) -> None:
    while True:
        try:
            line = lines.get_nowait()
        except queue.Empty:
            return
        print(f"\r< {line}")


def main() -> int:
    args = build_parser().parse_args()
    eol_map = {"LF": "\n", "CRLF": "\r\n", "CR": "\r"}
    stop_event = threading.Event()
    lines: queue.Queue[str] = queue.Queue()

    try:
        ser = serial.Serial(args.port, args.baud, timeout=0.2)
    except serial.SerialException as exc:
        print(f"Cannot open {args.port}: {exc}")
        return 1

    thread = threading.Thread(
        target=reader_loop, args=(ser, stop_event, lines), daemon=True
    )
    thread.start()

    print(f"Connected to {args.port} @ {args.baud}")
    print("Type commands and press Enter.")
    print("Built-ins: :help  :quit  :status  :arm  :stop")
    print("")

    try:
        while not stop_event.is_set():
            drain_lines(lines)
            try:
                cmd = input("> ").strip()
            except EOFError:
                cmd = ":quit"
            except KeyboardInterrupt:
                cmd = ":quit"

            if not cmd:
                continue

            if cmd == ":quit":
                break

            if cmd == ":help":
                print("Examples:")
                print("  status")
                print("  help")
                print("  target 90")
                print("  t 45")
                print("  power 35")
                print("  p 50")
                print("  arm")
                print("  stop")
                continue

            if cmd == ":status":
                cmd = "status"
            elif cmd == ":arm":
                cmd = "arm"
            elif cmd == ":stop":
                cmd = "stop"

            payload = cmd.encode("utf-8") + eol_map[args.eol].encode("ascii")
            try:
                ser.write(payload)
                ser.flush()
            except serial.SerialException as exc:
                print(f"[serial-error] {exc}")
                break

            time.sleep(0.05)
            drain_lines(lines)
    finally:
        stop_event.set()
        thread.join(timeout=0.5)
        if ser.is_open:
            ser.close()

    print("Disconnected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
