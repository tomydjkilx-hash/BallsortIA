package com.example.ballsortai

/**
 * Solver clássico de Ball Sort Puzzle.
 *
 * Representação: cada tubo é uma List<Int> onde o índice 0 = fundo do tubo
 * e o último elemento = topo (bola visível/acessível). Um tubo vazio é
 * simplesmente uma lista vazia.
 */
object Solver {

    data class Move(val from: Int, val to: Int)

    fun solve(tubes: List<List<Int>>, capacity: Int, maxDepth: Int = 400): List<Move>? {
        val start = tubes.map { it.toMutableList() }

        var limit = 40
        while (limit <= maxDepth) {
            val visited = HashSet<String>()
            val path = ArrayList<Move>()
            if (dfs(start, capacity, limit, visited, path)) {
                return path
            }
            limit *= 2
        }
        return null
    }

    private fun isSolved(tubes: List<MutableList<Int>>, capacity: Int): Boolean {
        return tubes.all { it.isEmpty() || (it.size == capacity && it.all { c -> c == it[0] }) }
    }

    private fun stateKey(tubes: List<MutableList<Int>>): String {
        return tubes.map { it.joinToString(",") }.sorted().joinToString("|")
    }

    private fun canMove(tubes: List<MutableList<Int>>, from: Int, to: Int, capacity: Int): Boolean {
        if (from == to) return false
        val src = tubes[from]
        val dst = tubes[to]
        if (src.isEmpty()) return false
        if (dst.size >= capacity) return false
        if (src.size == capacity && src.all { it == src[0] }) return false
        if (dst.isEmpty()) return true
        return dst.last() == src.last()
    }

    private fun scoreMove(tubes: List<MutableList<Int>>, from: Int, to: Int, capacity: Int): Int {
        val src = tubes[from]
        val dst = tubes[to]
        var score = 0
        if (dst.isEmpty()) score += 1 else score += 3
        val topColor = src.last()
        var countTop = 0
        for (i in src.indices.reversed()) {
            if (src[i] == topColor) countTop++ else break
        }
        if (dst.size + countTop == capacity) score += 5
        if (countTop == src.size) score += 2
        return score
    }

    private fun dfs(
        tubes: List<MutableList<Int>>,
        capacity: Int,
        depthLeft: Int,
        visited: HashSet<String>,
        path: ArrayList<Move>
    ): Boolean {
        if (isSolved(tubes, capacity)) return true
        if (depthLeft <= 0) return false

        val key = stateKey(tubes)
        if (!visited.add(key)) return false

        val n = tubes.size
        val candidates = ArrayList<Move>()
        for (from in 0 until n) {
            for (to in 0 until n) {
                if (canMove(tubes, from, to, capacity)) {
                    candidates.add(Move(from, to))
                }
            }
        }
        candidates.sortByDescending { scoreMove(tubes, it.from, it.to, capacity) }

        for (move in candidates) {
            val ball = tubes[move.from].removeAt(tubes[move.from].size - 1)
            tubes[move.to].add(ball)
            path.add(move)

            if (dfs(tubes, capacity, depthLeft - 1, visited, path)) {
                return true
            }

            path.removeAt(path.size - 1)
            tubes[move.to].removeAt(tubes[move.to].size - 1)
            tubes[move.from].add(ball)
        }

        return false
    }
}
