package com.toancao.flyingspawn;

/**
 * Các trạng thái trong vòng đời hành vi bay của Pokemon hệ FLYING.
 *
 * Sơ đồ chuyển trạng thái:
 *
 *   GROUNDED ──(takeoff)──▶ TAKING_OFF ──(done)──▶ FLYING
 *      ▲                                               │
 *      └──────────(smooth land)── LANDING ◀──(land)───┘
 */
public enum FlightState {

    /**
     * Đứng / đi bộ / nhìn xung quanh trên mặt đất.
     */
    GROUNDED,

    /**
     * Đang cất cánh theo quỹ đạo cong (arc), chưa vào chế độ bay hoàn toàn.
     * Trạng thái chuyển tiếp ngắn (~1-2 giây).
     */
    TAKING_OFF,

    /**
     * Bay liên tục trên không, có hướng chính và dao động sin.
     */
    FLYING,

    /**
     * Đang hạ cánh từ từ xuống đất, giảm tốc và độ cao dần.
     * Trạng thái chuyển tiếp ngắn (~1-2 giây).
     */
    LANDING
}
