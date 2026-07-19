package com.example.ballsortai

/**
 * Constantes ajustáveis da detecção automática do tabuleiro.
 * Se em algum aparelho/jogo a detecção sair errada, mexa aqui primeiro.
 */
object Config {
    // fração de cima/baixo da tela ignorada por ser interface do jogo
    // (nível, engrenagem, botões de desfazer/dica), não tabuleiro.
    // Ajuste se o layout do jogo colocar os tubos mais perto do topo/rodapé.
    const val HEADER_FRACTION = 0.27
    const val FOOTER_FRACTION = 0.16

    // distância euclidiana de RGB acima da qual um pixel é considerado
    // "diferente do fundo" (ou seja: bola, contorno de tubo, etc.)
    const val BG_THRESHOLD = 30

    // distância abaixo da qual duas cores de bola são consideradas iguais
    const val COLOR_MATCH_THRESHOLD = 40
}
