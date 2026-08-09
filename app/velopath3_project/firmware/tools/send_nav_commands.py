import argparse
import json
import sys
import time

try:
    import serial
except ImportError:
    print("pyserial is required: pip install pyserial", file=sys.stderr)
    raise


def build_payload(command: str, heading: int, seconds: int, distance: int) -> dict:
    if command == "advance_left":
        return {
            "type": "advance_turn",
            "direction": "left",
            "seconds_to_turn": seconds,
            "distance_meters": distance,
        }
    if command == "advance_right":
        return {
            "type": "advance_turn",
            "direction": "right",
            "seconds_to_turn": seconds,
            "distance_meters": distance,
        }
    if command == "turn_left":
        return {
            "type": "turn_now",
            "direction": "left",
            "magnetic_heading_degrees": heading,
        }
    if command == "turn_right":
        return {
            "type": "turn_now",
            "direction": "right",
            "magnetic_heading_degrees": heading,
        }
    if command == "finish":
        return {
            "type": "route_finished",
        }
    raise ValueError(f"Unsupported command: {command}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("port", help="Serial port, for example COM7")
    parser.add_argument(
        "command",
        choices=["advance_left", "advance_right", "turn_left", "turn_right", "finish"],
    )
    parser.add_argument("--baudrate", type=int, default=9600)
    parser.add_argument("--heading", type=int, default=90)
    parser.add_argument("--seconds", type=int, default=10)
    parser.add_argument("--distance", type=int, default=40)
    args = parser.parse_args()

    payload = build_payload(args.command, args.heading, args.seconds, args.distance)
    message = json.dumps(payload, separators=(",", ":")) + "\n"

    print(f"Opening {args.port} at {args.baudrate} baud")
    print(f"Sending: {message.strip()}")

    with serial.Serial(args.port, args.baudrate, timeout=1) as connection:
        time.sleep(0.2)
        connection.write(message.encode("utf-8"))
        connection.flush()

    print("Done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
