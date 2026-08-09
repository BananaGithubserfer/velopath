import argparse
import time

import serial


def read_until_idle(ser: serial.Serial, idle_s: float = 0.35, max_s: float = 2.5) -> bytes:
    started = time.time()
    last_rx = started
    buf = bytearray()
    while time.time() - started < max_s:
        chunk = ser.read(256)
        if chunk:
            buf.extend(chunk)
            last_rx = time.time()
            continue
        if (time.time() - last_rx) >= idle_s and buf:
            break
    return bytes(buf)


def send_cmd(ser: serial.Serial, cmd: str, wait_s: float = 0.35, max_s: float = 2.5) -> bytes:
    payload = (cmd + "\r\n").encode("ascii")
    sent = 0
    deadline = time.time() + max_s
    while sent < len(payload) and time.time() < deadline:
        try:
            n = ser.write(payload[sent:])
            if n:
                sent += n
            else:
                time.sleep(0.01)
        except serial.SerialTimeoutException:
            time.sleep(0.01)
    time.sleep(wait_s)
    return read_until_idle(ser, idle_s=0.25, max_s=max_s)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM5")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument(
        "--name",
        default="Velopath",
        help="BLE device name to set via AT+BLENAME",
    )
    parser.add_argument("--server-role", type=int, default=2, help="AT+BLEINIT role (2=server)")
    args = parser.parse_args()

    print(f"OPEN {args.port} @ {args.baud}")
    try:
        ser = serial.Serial(
            args.port,
            args.baud,
            timeout=0.15,
            write_timeout=0,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            xonxoff=False,
            rtscts=False,
            dsrdtr=False,
        )
    except Exception as exc:  # noqa: BLE001
        print(f"OPEN-ERROR {exc}")
        return 1

    try:
        ser.reset_input_buffer()
        ser.reset_output_buffer()
        # Warmup read (boot spam from ESP-AT after reset/plug)
        warmup = read_until_idle(ser, idle_s=0.2, max_s=1.2)
        if warmup:
            print(f"BOOT {warmup!r}")

        for cmd in ("AT", "AT+GMR"):
            resp = send_cmd(ser, cmd)
            print(f"> {cmd}")
            print(f"< {resp!r}")

        # Start BLE server workflow from the user's validated sequence.
        sequence = [
            f"AT+BLEINIT={args.server_role}",
            f'AT+BLENAME="{args.name}"',
            "AT+BLEGATTSSRVCRE",
            "AT+BLEGATTSSRVSTART",
            "AT+BLEADVSTART",
            "AT+BLEGATTSSRV?",
            "AT+BLEGATTSCHAR?",
        ]
        for cmd in sequence:
            resp = send_cmd(ser, cmd, wait_s=0.5, max_s=3.5)
            print(f"> {cmd}")
            print(f"< {resp!r}")

        # Optional transparent mode setup (safe to retry).
        spp_cfg = send_cmd(ser, "AT+BLESPPCFG=1,1,6,1,5", wait_s=0.5, max_s=3.5)
        print("> AT+BLESPPCFG=1,1,6,1,5")
        print(f"< {spp_cfg!r}")
        print("DONE")
        return 0
    finally:
        ser.close()


if __name__ == "__main__":
    raise SystemExit(main())
