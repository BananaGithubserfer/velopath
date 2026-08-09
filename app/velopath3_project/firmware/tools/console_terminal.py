import argparse
import threading

import serial


def reader(connection: serial.Serial) -> None:
    while True:
        data = connection.readline()
        if not data:
            continue
        print(data.decode("utf-8", errors="replace").rstrip())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("port", help="Serial port, for example COM7")
    parser.add_argument("--baudrate", type=int, default=9600)
    args = parser.parse_args()

    with serial.Serial(args.port, args.baudrate, timeout=0.2) as connection:
        print(f"Connected to {args.port} at {args.baudrate} baud")
        thread = threading.Thread(target=reader, args=(connection,), daemon=True)
        thread.start()

        while True:
            try:
                line = input("> ")
            except (EOFError, KeyboardInterrupt):
                print()
                break

            connection.write((line.strip() + "\n").encode("utf-8"))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
