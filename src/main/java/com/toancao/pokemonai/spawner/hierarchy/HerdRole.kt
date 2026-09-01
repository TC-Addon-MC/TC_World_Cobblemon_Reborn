package com.toancao.pokemonai.spawner.hierarchy

enum class HerdRole(val rankPriority: Int, val roleTag: String) {
    LEADER(100, "tc_herd_leader"),   // Con đầu đàn
    MEMBER(50, "tc_herd_member");    // Đàn em

    val isLeader: Boolean get() = this == LEADER
}
