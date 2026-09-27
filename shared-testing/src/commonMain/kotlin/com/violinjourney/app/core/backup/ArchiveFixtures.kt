package com.violinjourney.app.core.backup

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A copy made on an iPhone, for the Android test that reads it (spec 5.14: a copy from one platform opens on the other).
 * [FROM_IOS] is what the iOS `BackupWriter` (Zip.kt) writes of exactly these fields — the passport [manifest], the
 * database [database] as `db/violin.db` and the sound [sound] as `sessions/a.m4a`. `IosBackupArchiveTest` writes them
 * again on every run and fails when the bytes differ, with the new sample in its message: after a change of Zip.kt it is
 * put here and `:app:testDebugUnitTest` shows whether Android still reads it. The way back — a copy of Android read on
 * iOS — is `ANDROID_ARCHIVE` in `IosBackupArchiveTest`.
 */
@OptIn(ExperimentalEncodingApi::class)
object ArchiveFixtures {
    val manifest = BackupManifest(
        formatVersion = 1, appVersion = "1.0", databaseVersion = 13, createdAtEpochMs = 1_790_000_000_000, device = "iPhone 15 Pro Max",
        parts = setOf(BackupPart.DATA, BackupPart.AUDIO), counts = BackupCounts(sessions = 1, withSound = 1),
        bytes = mapOf(BackupPart.DATA to 11L, BackupPart.AUDIO to 4_000L),
    )

    val database: ByteArray get() = "hello world".encodeToByteArray()

    val sound: ByteArray get() = ByteArray(4_000) { ((it * 31 + 7) % 251).toByte() }

    fun fromIos(): ByteArray = Base64.decode(FROM_IOS)

    /** Written by Zip.kt: data descriptors, the media at level 0, the data deflated; every date 1980-01-01. */
    const val FROM_IOS =
        "UEsDBBQACAgIAAAAIQAAAAAAAAAAAAAAAAAMAAAAbWFuaWZlc3QudHh0PU5BDsIwDLv3FXsAQ6sAIQ49TNqFAwIJeEDWBRZtNFUb" +
        "BvyebkPkYltWbA/EPbmcnLADIXZ5DbZ7eqPVjcMDJBHwSS4L1YBADRGNXikbEAQbo7e74n+qwYEsGjq17DDTm+wUODvAW3kIEk1V" +
        "XspFea32RxUxxtQWU7xAh9EUyhPamcB9xgBWUl4Fn1FKYN/S5PQ4YJ9eXyTtmZ8u7VADNcijWX8E43LsMlr/1FRq1uPGL1BLBwgY" +
        "FWDhsgAAAPIAAABQSwMEFAAICAgAAAAhAAAAAAAAAAAAAAAAAAwAAABkYi92aW9saW4uZGLLSM3JyVcozy/KSQEAUEsHCIURSg0N" +
        "AAAACwAAAFBLAwQUAAgICAAAACEAAAAAAAAAAAAAAAAADgAAAHNlc3Npb25zL2EubTRhAaAPX/AHJkVkg6LB4AQjQmGAn77dASA/" +
        "Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIemxeQIJ0ZlhKPC4QUkQ2KB" +
        "oL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsnoDCtKaYinxuUJKEdmhaTD" +
        "4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAvTm2Mq8rpDSxLaomox+YK" +
        "KUhnhqXE4wcmRWSDosHgBCNCYYCfvt0BID9efZy72vkdPFt6mbjX9ho5WHeWtdTzFzZVdJOy0fAUM1JxkK/O7REwT26NrMvqDi1M" +
        "a4qpyOcLKkloh6bF5AgnRmWEo8LhBSRDYoGgv94CIUBffp282/oePVx7mrnY9xs6WXiXttX0GDdWdZSz0vEVNFNykbDP7hIxUG+O" +
        "rczrDy5NbIuqyegMK0ppiKfG5QkoR2aFpMPiBiVEY4KhwN8DIkFgf5693AAfPl18m7rZ+Bw7WnmYt9b1GThXdpW00/IWNVRzkrHQ" +
        "7xMyUXCPrs3sEC9ObYyryukNLEtqiajH5gopSGeGpcTjByZFZIOiweAEI0JhgJ++3QEgP159nLva+R08W3qZuNf2GjlYd5a11PMX" +
        "NlV0k7LR8BQzUnGQr87tETBPbo2sy+oOLUxriqnI5wsqSWiHpsXkCCdGZYSjwuEFJENigaC/3gIhQF9+nbzb+h49XHuaudj3GzpZ" +
        "eJe21fQYN1Z1lLPS8RU0U3KRsM/uEjFQb46tzOsPLk1si6rJ6AwrSmmIp8blCShHZoWkw+IGJURjgqHA3wMiQWB/nr3cAB8+XXyb" +
        "utn4HDtaeZi31vUZOFd2lbTT8hY1VHOSsdDvEzJRcI+uzewQL05tjKvK6Q0sS2qJqMfmCilIZ4alxOMHJkVkg6LB4AQjQmGAn77d" +
        "ASA/Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIemxeQIJ0ZlhKPC4QUk" +
        "Q2KBoL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsnoDCtKaYinxuUJKEdm" +
        "haTD4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAvTm2Mq8rpDSxLaomo" +
        "x+YKKUhnhqXE4wcmRWSDosHgBCNCYYCfvt0BID9efZy72vkdPFt6mbjX9ho5WHeWtdTzFzZVdJOy0fAUM1JxkK/O7REwT26NrMvq" +
        "Di1Ma4qpyOcLKkloh6bF5AgnRmWEo8LhBSRDYoGgv94CIUBffp282/oePVx7mrnY9xs6WXiXttX0GDdWdZSz0vEVNFNykbDP7hIx" +
        "UG+OrczrDy5NbIuqyegMK0ppiKfG5QkoR2aFpMPiBiVEY4KhwN8DIkFgf5693AAfPl18m7rZ+Bw7WnmYt9b1GThXdpW00/IWNVRz" +
        "krHQ7xMyUXCPrs3sEC9ObYyryukNLEtqiajH5gopSGeGpcTjByZFZIOiweAEI0JhgJ++3QEgP159nLva+R08W3qZuNf2GjlYd5a1" +
        "1PMXNlV0k7LR8BQzUnGQr87tETBPbo2sy+oOLUxriqnI5wsqSWiHpsXkCCdGZYSjwuEFJENigaC/3gIhQF9+nbzb+h49XHuaudj3" +
        "GzpZeJe21fQYN1Z1lLPS8RU0U3KRsM/uEjFQb46tzOsPLk1si6rJ6AwrSmmIp8blCShHZoWkw+IGJURjgqHA3wMiQWB/nr3cAB8+" +
        "XXybutn4HDtaeZi31vUZOFd2lbTT8hY1VHOSsdDvEzJRcI+uzewQL05tjKvK6Q0sS2qJqMfmCilIZ4alxOMHJkVkg6LB4AQjQmGA" +
        "n77dASA/Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIemxeQIJ0ZlhKPC" +
        "4QUkQ2KBoL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsnoDCtKaYinxuUJ" +
        "KEdmhaTD4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAvTm2Mq8rpDSxL" +
        "aomox+YKKUhnhqXE4wcmRWSDosHgBCNCYYCfvt0BID9efZy72vkdPFt6mbjX9ho5WHeWtdTzFzZVdJOy0fAUM1JxkK/O7REwT26N" +
        "rMvqDi1Ma4qpyOcLKkloh6bF5AgnRmWEo8LhBSRDYoGgv94CIUBffp282/oePVx7mrnY9xs6WXiXttX0GDdWdZSz0vEVNFNykbDP" +
        "7hIxUG+OrczrDy5NbIuqyegMK0ppiKfG5QkoR2aFpMPiBiVEY4KhwN8DIkFgf5693AAfPl18m7rZ+Bw7WnmYt9b1GThXdpW00/IW" +
        "NVRzkrHQ7xMyUXCPrs3sEC9ObYyryukNLEtqiajH5gopSGeGpcTjByZFZIOiweAEI0JhgJ++3QEgP159nLva+R08W3qZuNf2GjlY" +
        "d5a11PMXNlV0k7LR8BQzUnGQr87tETBPbo2sy+oOLUxriqnI5wsqSWiHpsXkCCdGZYSjwuEFJENigaC/3gIhQF9+nbzb+h49XHua" +
        "udj3GzpZeJe21fQYN1Z1lLPS8RU0U3KRsM/uEjFQb46tzOsPLk1si6rJ6AwrSmmIp8blCShHZoWkw+IGJURjgqHA3wMiQWB/nr3c" +
        "AB8+XXybutn4HDtaeZi31vUZOFd2lbTT8hY1VHOSsdDvEzJRcI+uzewQL05tjKvK6Q0sS2qJqMfmCilIZ4alxOMHJkVkg6LB4AQj" +
        "QmGAn77dASA/Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIemxeQIJ0Zl" +
        "hKPC4QUkQ2KBoL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsnoDCtKaYin" +
        "xuUJKEdmhaTD4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAvTm2Mq8rp" +
        "DSxLaomox+YKKUhnhqXE4wcmRWSDosHgBCNCYYCfvt0BID9efZy72vkdPFt6mbjX9ho5WHeWtdTzFzZVdJOy0fAUM1JxkK/O7REw" +
        "T26NrMvqDi1Ma4qpyOcLKkloh6bF5AgnRmWEo8LhBSRDYoGgv94CIUBffp282/oePVx7mrnY9xs6WXiXttX0GDdWdZSz0vEVNFNy" +
        "kbDP7hIxUG+OrczrDy5NbIuqyegMK0ppiKfG5QkoR2aFpMPiBiVEY4KhwN8DIkFgf5693AAfPl18m7rZ+Bw7WnmYt9b1GThXdpW0" +
        "0/IWNVRzkrHQ7xMyUXCPrs3sEC9ObYyryukNLEtqiajH5gopSGeGpcTjByZFZIOiweAEI0JhgJ++3QEgP159nLva+R08W3qZuNf2" +
        "GjlYd5a11PMXNlV0k7LR8BQzUnGQr87tETBPbo2sy+oOLUxriqnI5wsqSWiHpsXkCCdGZYSjwuEFJENigaC/3gIhQF9+nbzb+h49" +
        "XHuaudj3GzpZeJe21fQYN1Z1lLPS8RU0U3KRsM/uEjFQb46tzOsPLk1si6rJ6AwrSmmIp8blCShHZoWkw+IGJURjgqHA3wMiQWB/" +
        "nr3cAB8+XXybutn4HDtaeZi31vUZOFd2lbTT8hY1VHOSsdDvEzJRcI+uzewQL05tjKvK6Q0sS2qJqMfmCilIZ4alxOMHJkVkg6LB" +
        "4AQjQmGAn77dASA/Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIemxeQI" +
        "J0ZlhKPC4QUkQ2KBoL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsnoDCtK" +
        "aYinxuUJKEdmhaTD4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAvTm2M" +
        "q8rpDSxLaomox+YKKUhnhqXE4wcmRWSDosHgBCNCYYCfvt0BID9efZy72vkdPFt6mbjX9ho5WHeWtdTzFzZVdJOy0fAUM1JxkK/O" +
        "7REwT26NrMvqDi1Ma4qpyOcLKkloh6bF5AgnRmWEo8LhBSRDYoGgv94CIUBffp282/oePVx7mrnY9xs6WXiXttX0GDdWdZSz0vEV" +
        "NFNykbDP7hIxUG+OrczrDy5NbIuqyegMK0ppiKfG5QkoR2aFpMPiBiVEY4KhwN8DIkFgf5693AAfPl18m7rZ+Bw7WnmYt9b1GThX" +
        "dpW00/IWNVRzkrHQ7xMyUXCPrs3sEC9ObYyryukNLEtqiajH5gopSGeGpcTjByZFZIOiweAEI0JhgJ++3QEgP159nLva+R08W3qZ" +
        "uNf2GjlYd5a11PMXNlV0k7LR8BQzUnGQr87tETBPbo2sy+oOLUxriqnI5wsqSWiHpsXkCCdGZYSjwuEFJENigaC/3gIhQF9+nbzb" +
        "+h49XHuaudj3GzpZeJe21fQYN1Z1lLPS8RU0U3KRsM/uEjFQb46tzOsPLk1si6rJ6AwrSmmIp8blCShHZoWkw+IGJURjgqHA3wMi" +
        "QWB/nr3cAB8+XXybutn4HDtaeZi31vUZOFd2lbTT8hY1VHOSsdDvEzJRcI+uzewQL05tjKvK6Q0sS2qJqMfmCilIZ4alxOMHJkVk" +
        "g6LB4AQjQmGAn77dASA/Xn2cu9r5HTxbepm41/YaOVh3lrXU8xc2VXSTstHwFDNScZCvzu0RME9ujazL6g4tTGuKqcjnCypJaIem" +
        "xeQIJ0ZlhKPC4QUkQ2KBoL/eAiFAX36dvNv6Hj1ce5q52PcbOll4l7bV9Bg3VnWUs9LxFTRTcpGwz+4SMVBvjq3M6w8uTWyLqsno" +
        "DCtKaYinxuUJKEdmhaTD4gYlRGOCocDfAyJBYH+evdwAHz5dfJu62fgcO1p5mLfW9Rk4V3aVtNPyFjVUc5Kx0O8TMlFwj67N7BAv" +
        "Tm2Mq8rpUEsHCA9xbrylDwAAoA8AAFBLAwQUAAgICAAAACEAAAAAAAAAAAAAAAAADAAAAGNvbXBsZXRlLnR4dEvNKynKTC22NeJK" +
        "qiwB0iYGhoZcAFBLBwgbkdPmFQAAABUAAABQSwECFAAUAAgICAAAACEAGBVg4bIAAADyAAAADAAAAAAAAAAAAAAAAAAAAAAAbWFu" +
        "aWZlc3QudHh0UEsBAhQAFAAICAgAAAAhAIURSg0NAAAACwAAAAwAAAAAAAAAAAAAAAAA7AAAAGRiL3Zpb2xpbi5kYlBLAQIUABQA" +
        "CAgIAAAAIQAPcW68pQ8AAKAPAAAOAAAAAAAAAAAAAAAAADMBAABzZXNzaW9ucy9hLm00YVBLAQIUABQACAgIAAAAIQAbkdPmFQAA" +
        "ABUAAAAMAAAAAAAAAAAAAAAAABQRAABjb21wbGV0ZS50eHRQSwUGAAAAAAQABADqAAAAYxEAAAAA"
}
