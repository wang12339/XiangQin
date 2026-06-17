package com.xiangqin.app.util

/**
 * 将 WiFi 频率 (MHz) 转换为信道号
 */
fun frequencyToChannel(freq: Int): Int = when {
    freq in 2412..2484  -> (freq - 2412) / 5 + 1   // 2.4GHz: 信道 1-13
    freq == 2484        -> 14                       // 2.4GHz: 信道 14
    freq in 5170..5250  -> (freq - 5170) / 5 + 34  // 5GHz 低频: 信道 34-64
    freq in 5250..5330  -> (freq - 5250) / 5 + 105 // 5GHz: 信道 105-121
    freq in 5490..5730  -> (freq - 5490) / 5 + 9   // 5GHz UNII-2e
    freq in 5735..5850  -> (freq - 5735) / 5 + 149 // 5GHz 高频: 信道 149-173
    freq in 5955..7115  -> (freq - 5955) / 5 + 1   // 6GHz (WiFi 6E): 信道 1-233
    else -> 0
}
