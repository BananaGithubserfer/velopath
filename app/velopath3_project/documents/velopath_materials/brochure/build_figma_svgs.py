from __future__ import annotations

import base64
from pathlib import Path

import qrcode
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.colormasks import RadialGradiantColorMask
from qrcode.image.styles.moduledrawers.pil import RoundedModuleDrawer


BASE = Path(__file__).resolve().parent
ASSETS = BASE / "assets"
LOGO_PATH = ASSETS / "velopath-logo-embed.png"
DEVICE_PATH = ASSETS / "velopath-device-photo.png"
QR_PATH = ASSETS / "velopath-telegram-qr.png"
TELEGRAM_URL = "https://t.me/velopath"


def ensure_qr() -> None:
    qr = qrcode.QRCode(
        version=4,
        border=2,
        box_size=18,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
    )
    qr.add_data(TELEGRAM_URL)
    qr.make(fit=True)
    image = qr.make_image(
        image_factory=StyledPilImage,
        module_drawer=RoundedModuleDrawer(),
        color_mask=RadialGradiantColorMask(
            back_color=(255, 255, 255),
            center_color=(74, 145, 255),
            edge_color=(191, 88, 177),
        ),
    )
    image.save(QR_PATH)


def data_uri(path: Path) -> str:
    suffix = path.suffix.lower().lstrip(".") or "png"
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/{suffix};base64,{data}"


OUTSIDE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="3508" height="2480" viewBox="0 0 3508 2480">
  <defs>
    <linearGradient id="coverBg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#0C5A3C"/>
      <stop offset="100%" stop-color="#082E22"/>
    </linearGradient>
    <linearGradient id="sandBg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#F8F0DF"/>
      <stop offset="100%" stop-color="#EFDDB7"/>
    </linearGradient>
    <linearGradient id="mintBg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#EDF7F2"/>
      <stop offset="100%" stop-color="#DDEEE4"/>
    </linearGradient>
    <style>
      .h1 { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 138px; font-weight: 800; fill: white; }
      .h2 { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 88px; font-weight: 700; fill: #173A2D; }
      .h3 { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 60px; font-weight: 700; fill: #173A2D; }
      .kicker { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #567368; text-transform: uppercase; }
      .body { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 50px; fill: #2F4E44; }
      .bodyDark { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 52px; font-weight: 700; fill: #173A2D; }
      .bodyLight { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 50px; fill: #E5F5ED; }
      .small { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 38px; fill: #5D776D; }
      .smallLight { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 36px; fill: #CFE7DC; }
      .fold { stroke: #8FAEA2; stroke-width: 4; stroke-dasharray: 18 18; opacity: 0.6; }
      .safe { stroke: #B8CBC2; stroke-width: 3; stroke-dasharray: 10 12; opacity: 0.5; fill: none; }
    </style>
  </defs>

  <rect width="3508" height="2480" fill="#F5F0E6"/>
  <rect x="0" y="0" width="1169" height="2480" fill="url(#mintBg)"/>
  <rect x="1169" y="0" width="1170" height="2480" fill="url(#sandBg)"/>
  <rect x="2339" y="0" width="1169" height="2480" fill="url(#coverBg)"/>
  <line x1="1169" y1="0" x2="1169" y2="2480" class="fold"/>
  <line x1="2339" y1="0" x2="2339" y2="2480" class="fold"/>
  <rect x="110" y="110" width="948" height="2260" rx="28" class="safe"/>
  <rect x="1282" y="110" width="944" height="2260" rx="28" class="safe"/>
  <rect x="2436" y="118" width="972" height="2240" rx="32" class="safe"/>

  <g transform="translate(128 132)">
    <text class="kicker" x="0" y="0">Fold-in panel</text>
    <text class="h2" x="0" y="118">Для чего</text>
    <text class="h2" x="0" y="208">это устройство</text>

    <text class="body" x="0" y="332">VeloPath переносит навигацию</text>
    <text class="body" x="0" y="392">с экрана телефона на руль,</text>
    <text class="body" x="0" y="452">чтобы взгляд оставался на дороге.</text>

    <rect x="0" y="534" width="872" height="298" rx="34" fill="#0B6848"/>
    <text x="48" y="612" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D5EFE3;">КЛЮЧЕВАЯ ЦЕННОСТЬ</text>
    <text class="bodyLight" x="48" y="700">Виброподсказки вместо</text>
    <text class="bodyLight" x="48" y="760">постоянного взгляда в смартфон.</text>

    <text class="h3" x="0" y="944">Что получает райдер</text>
    <circle cx="18" cy="1034" r="12" fill="#0B5D3B"/>
    <text class="bodyDark" x="50" y="1050">Поворотные сигналы на руле</text>
    <circle cx="18" cy="1128" r="12" fill="#0B5D3B"/>
    <text class="bodyDark" x="50" y="1144">Работу без постоянного взгляда на экран</text>
    <circle cx="18" cy="1222" r="12" fill="#0B5D3B"/>
    <text class="bodyDark" x="50" y="1238">BLE-связь со смартфоном</text>
    <circle cx="18" cy="1316" r="12" fill="#0B5D3B"/>
    <text class="bodyDark" x="50" y="1332">Компактный модуль для прототипа и демо</text>

    <rect x="0" y="1450" width="882" height="662" rx="36" fill="#FFFFFF"/>
    <rect x="32" y="1482" width="392" height="392" rx="42" fill="#F6F7FB"/>
    <image x="56" y="1506" width="344" height="344" href="__QR__" preserveAspectRatio="xMidYMid meet"/>
    <text class="h3" x="444" y="1544">Telegram</text>
    <text class="body" x="444" y="1624">Сканируйте QR-код</text>
    <text class="body" x="444" y="1684">для перехода в канал</text>
    <text class="bodyDark" x="444" y="1768">@velopath</text>
    <text class="small" x="444" y="1848">Ссылка зашита в макет</text>
    <text class="small" x="444" y="1898">и сохранится при импорте в Figma.</text>
    <text class="small" x="444" y="1988">Красивый QR встроен внутрь SVG.</text>
  </g>

  <g transform="translate(1288 132)">
    <text class="kicker" x="0" y="0">Back panel</text>
    <text class="h2" x="0" y="118">Устройство</text>
    <text class="h2" x="0" y="208">в центре проекта</text>

    <text class="body" x="0" y="332">Это не просто приложение.</text>
    <text class="body" x="0" y="392">Главный продукт здесь -</text>
    <text class="body" x="0" y="452">рулевой навигационный модуль.</text>

    <rect x="0" y="548" width="1060" height="420" rx="34" fill="#F5E7C9"/>
    <text class="h3" x="52" y="636">Состав модуля</text>
    <text class="bodyDark" x="52" y="730">STM32F103RGTx</text>
    <text class="bodyDark" x="52" y="806">WH-BLE106</text>
    <text class="bodyDark" x="52" y="882">LIS3MDL</text>
    <text class="small" x="420" y="730">микроконтроллер управления</text>
    <text class="small" x="420" y="806">BLE UART bridge</text>
    <text class="small" x="420" y="882">электронный компас</text>

    <rect x="0" y="1036" width="1060" height="516" rx="34" fill="#FFFFFF"/>
    <text class="h3" x="52" y="1124">Что делает модуль</text>
    <text class="body" x="52" y="1210">Получает навигационную команду</text>
    <text class="body" x="52" y="1270">по BLE и переводит её в тактильный сигнал.</text>
    <text class="body" x="52" y="1374">Левый и правый каналы вибрации</text>
    <text class="body" x="52" y="1434">подсказывают направление без экрана.</text>
    <text class="small" x="52" y="1522">Команды firmware: advance_turn, turn_now, route_finished.</text>

    <rect x="0" y="1624" width="1060" height="484" rx="34" fill="#123F58"/>
    <text x="52" y="1710" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D7EEF8;">ДЛЯ ПРОИЗВОДИТЕЛЯ</text>
    <text x="52" y="1806" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 52px; font-weight: 700; fill: white;">Фокус на корпусе, креплении,</text>
    <text x="52" y="1872" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 52px; font-weight: 700; fill: white;">виброотклике и эргономике руля.</text>
    <text x="52" y="1978" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 42px; fill: #D7EEF8;">Приложение здесь нужно как источник маршрута</text>
    <text x="52" y="2038" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 42px; fill: #D7EEF8;">и BLE-команд для аппаратного модуля.</text>
  </g>

  <g transform="translate(2428 118)">
    <text class="kicker" x="0" y="0" style="fill: #D0E9DD;">Cover</text>
    <image x="0" y="62" width="248" height="248" href="__LOGO__" preserveAspectRatio="xMidYMid meet"/>
    <text class="h1" x="0" y="420">VeloPath</text>
    <text x="0" y="516" style="font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 68px; font-weight: 700; fill: #DDF2E8;">Handlebar Navigation Device</text>
    <text class="bodyLight" x="0" y="620">Навигационный модуль для велосипеда</text>
    <text class="bodyLight" x="0" y="680">с BLE-связью и тактильными подсказками.</text>

    <rect x="0" y="760" width="914" height="1026" rx="54" fill="#FFFFFF"/>
    <image x="92" y="818" width="730" height="730" href="__DEVICE__" preserveAspectRatio="xMidYMid meet"/>
    <rect x="72" y="1568" width="770" height="136" rx="30" fill="#DCEEE4"/>
    <text x="110" y="1644" style="font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 58px; font-weight: 700; fill: #173A2D;">Навигация на руле, а не на экране</text>

    <rect x="0" y="1860" width="914" height="298" rx="34" fill="#0D4D37"/>
    <text x="48" y="1944" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D7EEE5;">ОСНОВНАЯ ИДЕЯ</text>
    <text class="bodyLight" x="48" y="2034">Аппаратное устройство ведёт райдера</text>
    <text class="bodyLight" x="48" y="2094">по маршруту, а софт только подаёт команды.</text>
  </g>
</svg>
"""


INSIDE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="3508" height="2480" viewBox="0 0 3508 2480">
  <defs>
    <linearGradient id="bg1" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#EEF7F2"/>
      <stop offset="100%" stop-color="#E1F0E7"/>
    </linearGradient>
    <linearGradient id="bg2" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#F8F0DF"/>
      <stop offset="100%" stop-color="#EEDDB9"/>
    </linearGradient>
    <linearGradient id="bg3" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#E1F1F8"/>
      <stop offset="100%" stop-color="#D6EAF4"/>
    </linearGradient>
    <style>
      .h2 { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 84px; font-weight: 700; fill: #173A2D; }
      .h3 { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 56px; font-weight: 700; fill: #173A2D; }
      .body { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 48px; fill: #2F4E44; }
      .bodyStrong { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 50px; font-weight: 700; fill: #173A2D; }
      .small { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 38px; fill: #567166; }
      .caps { font-family: 'DejaVu Sans', Arial, Helvetica, sans-serif; font-size: 30px; font-weight: 700; letter-spacing: 2px; fill: #0B6848; text-transform: uppercase; }
      .fold { stroke: #96B3A8; stroke-width: 4; stroke-dasharray: 18 18; opacity: 0.6; }
      .safe { stroke: #B8CBC2; stroke-width: 3; stroke-dasharray: 10 12; opacity: 0.5; fill: none; }
    </style>
  </defs>

  <rect width="3508" height="2480" fill="#F6F3EC"/>
  <rect x="70" y="72" width="1050" height="2336" rx="44" fill="url(#bg1)"/>
  <rect x="1228" y="72" width="1050" height="2336" rx="44" fill="url(#bg2)"/>
  <rect x="2388" y="72" width="1050" height="2336" rx="44" fill="url(#bg3)"/>
  <rect x="108" y="110" width="974" height="2260" rx="28" class="safe"/>
  <rect x="1266" y="110" width="974" height="2260" rx="28" class="safe"/>
  <rect x="2426" y="110" width="974" height="2260" rx="28" class="safe"/>
  <line x1="1169" y1="0" x2="1169" y2="2480" class="fold"/>
  <line x1="2339" y1="0" x2="2339" y2="2480" class="fold"/>

  <g transform="translate(132 132)">
    <text class="caps" x="0" y="0">Panel 1</text>
    <text class="h2" x="0" y="108">Зачем нужен</text>
    <text class="h2" x="0" y="198">аппаратный модуль</text>

    <text class="body" x="0" y="320">Телефон неудобно использовать</text>
    <text class="body" x="0" y="378">как основной интерфейс навигации</text>
    <text class="body" x="0" y="436">во время движения на велосипеде.</text>

    <rect x="0" y="520" width="900" height="324" rx="30" fill="#FFFFFF"/>
    <text class="h3" x="48" y="604">Проблема</text>
    <text class="body" x="48" y="688">Экран отвлекает, а крепление смартфона</text>
    <text class="body" x="48" y="746">не решает вопрос безопасного восприятия поворотов.</text>

    <rect x="0" y="912" width="900" height="1220" rx="34" fill="#0B6848"/>
    <text x="52" y="996" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D7EEE5;">РЕШЕНИЕ VELOPATH</text>
    <text x="52" y="1090" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 52px; font-weight: 700; fill: white;">Компактный модуль на руле</text>
    <text x="52" y="1154" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 52px; font-weight: 700; fill: white;">с виброиндикацией поворотов.</text>
    <image x="88" y="1210" width="724" height="724" href="__DEVICE__" preserveAspectRatio="xMidYMid meet"/>
    <text x="52" y="2004" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 40px; fill: #D7EEE5;">Устройство становится главным интерфейсом</text>
    <text x="52" y="2058" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 40px; fill: #D7EEE5;">между маршрутом и райдером.</text>
  </g>

  <g transform="translate(1292 132)">
    <text class="caps" x="0" y="0">Panel 2</text>
    <text class="h2" x="0" y="108">Что внутри</text>
    <text class="h2" x="0" y="198">устройства</text>

    <rect x="0" y="286" width="886" height="252" rx="28" fill="#0B6848"/>
    <text x="52" y="366" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D8F3E8;">АРХИТЕКТУРА</text>
    <text x="52" y="450" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 48px; fill: white;">Смартфон строит маршрут,</text>
    <text x="52" y="510" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 48px; fill: white;">а модуль на руле подаёт сигнал поворота.</text>

    <path d="M110 700 L110 1880" stroke="#7FB59F" stroke-width="10" stroke-dasharray="12 18"/>

    <circle cx="110" cy="740" r="72" fill="#0B6848"/>
    <text x="82" y="778" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 72px; font-weight: 700; fill: white;">1</text>
    <text class="bodyStrong" x="240" y="722">STM32F103RGTx</text>
    <text class="small" x="240" y="774">Управляет логикой модуля и выходами.</text>

    <circle cx="110" cy="1000" r="72" fill="#2698D0"/>
    <text x="82" y="1038" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 72px; font-weight: 700; fill: white;">2</text>
    <text class="bodyStrong" x="240" y="982">WH-BLE106</text>
    <text class="small" x="240" y="1034">Принимает BLE-команды от приложения.</text>

    <circle cx="110" cy="1260" r="72" fill="#8A6A3C"/>
    <text x="82" y="1298" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 72px; font-weight: 700; fill: white;">3</text>
    <text class="bodyStrong" x="240" y="1242">LIS3MDL</text>
    <text class="small" x="240" y="1294">Компас помогает учитывать ориентацию.</text>

    <circle cx="110" cy="1520" r="72" fill="#3E725E"/>
    <text x="82" y="1558" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 72px; font-weight: 700; fill: white;">4</text>
    <text class="bodyStrong" x="240" y="1502">Левый и правый виброканал</text>
    <text class="small" x="240" y="1554">Тактильно указывают направление манёвра.</text>

    <circle cx="110" cy="1780" r="72" fill="#5B9A73"/>
    <text x="82" y="1818" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 72px; font-weight: 700; fill: white;">5</text>
    <text class="bodyStrong" x="240" y="1762">Команды прошивки</text>
    <text class="small" x="240" y="1814">advance_turn, turn_now, route_finished.</text>
  </g>

  <g transform="translate(2450 132)">
    <text class="caps" x="0" y="0">Panel 3</text>
    <text class="h2" x="0" y="108">Роль приложения</text>
    <text class="h2" x="0" y="198">в системе</text>

    <rect x="0" y="286" width="892" height="288" rx="30" fill="#FFFFFF"/>
    <text class="h3" x="48" y="372">Софт здесь вторичен</text>
    <text class="body" x="48" y="454">Приложение нужно, чтобы найти точку,</text>
    <text class="body" x="48" y="512">построить маршрут и передать сигнал на руль.</text>

    <rect x="0" y="646" width="418" height="884" rx="32" fill="#FFFFFF"/>
    <rect x="32" y="684" width="354" height="808" rx="44" fill="#0F1720"/>
    <rect x="56" y="728" width="306" height="718" rx="28" fill="#F4F8FA"/>
    <rect x="82" y="764" width="254" height="96" rx="24" fill="#0B6848"/>
    <text x="110" y="818" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 22px; font-weight: 700; fill: white;">Поиск точки</text>
    <rect x="82" y="900" width="254" height="64" rx="18" fill="#FFFFFF"/>
    <text x="106" y="942" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 22px; fill: #6B7F77;">Красная площадь</text>
    <rect x="82" y="996" width="254" height="148" rx="22" fill="#DDEEF7"/>
    <path d="M114 1090 C144 1052, 176 1044, 206 1060 C238 1078, 268 1062, 304 1020" fill="none" stroke="#2995CC" stroke-width="10" stroke-linecap="round"/>
    <circle cx="114" cy="1090" r="10" fill="#0B6848"/>
    <circle cx="304" cy="1020" r="10" fill="#8A6A3C"/>
    <text x="100" y="1184" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 20px; font-weight: 700; fill: #173A2D;">Превью маршрута</text>
    <rect x="82" y="1230" width="254" height="94" rx="22" fill="#0B6848"/>
    <text x="110" y="1288" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 22px; fill: white;">BLE ready</text>

    <rect x="472" y="646" width="418" height="884" rx="32" fill="#FFFFFF"/>
    <rect x="504" y="684" width="354" height="808" rx="44" fill="#0F1720"/>
    <rect x="528" y="728" width="306" height="718" rx="28" fill="#F7F3E8"/>
    <rect x="554" y="768" width="252" height="64" rx="18" fill="#DCEFF7"/>
    <text x="582" y="810" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 20px; font-weight: 700; fill: #1B4D6B;">Маршрут активен</text>
    <rect x="554" y="870" width="252" height="296" rx="24" fill="#E3F1E8"/>
    <path d="M584 1112 C614 1060, 654 1028, 694 1036 C730 1044, 752 1008, 782 948" fill="none" stroke="#2B8A69" stroke-width="12" stroke-linecap="round"/>
    <circle cx="584" cy="1112" r="11" fill="#0B6848"/>
    <circle cx="782" cy="948" r="11" fill="#2698D0"/>
    <rect x="554" y="1200" width="252" height="92" rx="22" fill="#FFFFFF"/>
    <text x="578" y="1238" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 18px; fill: #63786E;">Следующий манёвр</text>
    <text x="578" y="1274" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 22px; font-weight: 700; fill: #173A2D;">Поворот налево</text>
    <rect x="554" y="1318" width="252" height="112" rx="22" fill="#123F58"/>
    <text x="578" y="1362" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 18px; fill: #D7EEF8;">Связь с устройством</text>
    <text x="578" y="1400" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 20px; font-weight: 700; fill: white;">BLE-модуль подключён</text>

    <rect x="0" y="1612" width="892" height="462" rx="34" fill="#123F58"/>
    <text x="52" y="1696" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 32px; font-weight: 700; letter-spacing: 2px; fill: #D7EEF8;">ПОЗИЦИЯ ПРОЕКТА</text>
    <text x="52" y="1792" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 48px; font-weight: 700; fill: white;">Главный носитель ценности -</text>
    <text x="52" y="1852" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 48px; font-weight: 700; fill: white;">само устройство на руле.</text>
    <text x="52" y="1948" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 40px; fill: #D7EEF8;">Приложение показано как supporting-слой:</text>
    <text x="52" y="2002" style="font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 40px; fill: #D7EEF8;">маршрут, BLE и интерфейс настройки.</text>
  </g>
</svg>
"""


def main() -> None:
    ensure_qr()
    replacements = {
        "__LOGO__": data_uri(LOGO_PATH),
        "__DEVICE__": data_uri(DEVICE_PATH),
        "__QR__": data_uri(QR_PATH),
    }

    outside = OUTSIDE_SVG
    inside = INSIDE_SVG
    for key, value in replacements.items():
        outside = outside.replace(key, value)
        inside = inside.replace(key, value)

    (BASE / "velopath-brochure-outside-figma.svg").write_text(outside, encoding="utf-8")
    (BASE / "velopath-brochure-inside-figma.svg").write_text(inside, encoding="utf-8")
    print("ok")


if __name__ == "__main__":
    main()

