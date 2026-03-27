package com.example.anda.domain

object CnaeRiskMapper {

    // Mapeamento por CNPJ exato para atividades especificas criticas
    private val exactMatches = mapOf(
        "25.11-0-00" to 3,
        "10.11-2-01" to 3,
        "41.20-4-00" to 4,
        "86.40-2-05" to 2,
        "47.11-3-02" to 2,
        "62.01-5-01" to 1
    )

    // Regras por prefixo de 2 digitos seguindo a NR-04 Quadro I
    private val prefixRules = mapOf(
        "01" to 3, // Agricultura e pecuaria
        "02" to 3, // Silvicultura
        "03" to 3, // Pesca e aquicultura
        "05" to 4, // Extracao de carvao
        "06" to 4, // Extracao de petroleo e gas
        "07" to 4, // Extracao de minerio de ferro
        "08" to 4, // Extracao de outros minerais
        "09" to 4, // Atividades de apoio a extracao
        "10" to 3, // Fabricacao de produtos alimenticios
        "11" to 3, // Fabricacao de bebidas
        "12" to 3, // Fabricacao de produtos do fumo
        "13" to 3, // Fabricacao de produtos texteis
        "14" to 2, // Confeccao de artigos do vestuario
        "15" to 3, // Curtimento de couro
        "16" to 3, // Fabricacao de produtos de madeira
        "17" to 3, // Fabricacao de celulose e papel
        "18" to 2, // Impressao e reproducao de gravacoes
        "19" to 4, // Fabricacao de coque e derivados do petroleo
        "20" to 4, // Fabricacao de produtos quimicos
        "21" to 3, // Fabricacao de produtos farmoquimicos
        "22" to 3, // Fabricacao de produtos de borracha e plastico
        "23" to 3, // Fabricacao de produtos de minerais nao-metalicos
        "24" to 4, // Metalurgia
        "25" to 3, // Fabricacao de produtos de metal
        "26" to 2, // Fabricacao de equipamentos de informatica
        "27" to 3, // Fabricacao de maquinas e aparelhos eletricos
        "28" to 3, // Fabricacao de maquinas e equipamentos
        "29" to 3, // Fabricacao de veiculos automotores
        "30" to 3, // Fabricacao de outros equipamentos de transporte
        "31" to 2, // Fabricacao de moveis
        "32" to 2, // Fabricacao de produtos diversos
        "33" to 3, // Manutencao de maquinas e equipamentos
        "35" to 3, // Eletricidade e gas - NR-10
        "36" to 2, // Captacao, tratamento de agua
        "37" to 3, // Esgoto, atividades relacionadas
        "38" to 3, // Coleta, tratamento de residuos
        "39" to 3, // Descontaminacao e gestao de residuos
        "41" to 4, // Construcao de edificios - NR-18
        "42" to 4, // Obras de infraestrutura
        "43" to 4, // Servicos especializados de construcao - NR-18
        "45" to 3, // Comercio de veiculos
        "46" to 2, // Comercio por atacado
        "47" to 2, // Comercio varejista
        "49" to 3, // Transporte terrestre
        "50" to 3, // Transporte aquaviario
        "51" to 3, // Transporte aereo
        "52" to 3, // Armazenamento e atividades auxiliares
        "53" to 2, // Correios
        "55" to 1, // Alojamento
        "56" to 1, // Alimentacao
        "58" to 1, // Edicao e impressao
        "61" to 1, // Telecomunicacoes
        "62" to 1, // Desenvolvimento de sistemas - TI
        "63" to 1, // Atividades de servicos de informacao
        "64" to 1, // Servicos financeiros
        "65" to 1, // Seguros e previdencia
        "66" to 1, // Atividades auxiliares de servicos financeiros
        "68" to 1, // Atividades imobiliarias
        "69" to 1, // Atividades juridicas e contabilidade
        "70" to 1, // Atividades de sedes de empresas
        "71" to 2, // Arquitetura e engenharia
        "72" to 1, // Pesquisa e desenvolvimento cientifico
        "73" to 1, // Publicidade e pesquisa de mercado
        "74" to 1, // Outras atividades profissionais
        "75" to 2, // Atividades veterinarias
        "77" to 2, // Alugueis
        "78" to 2, // Selecao e agenciamento de trabalhadores
        "79" to 2, // Agencias de viagem
        "80" to 3, // Vigilancia e seguranca
        "81" to 2, // Servicos de edificios e paisagismo
        "82" to 1, // Servicos de escritorio
        "84" to 2, // Administracao publica
        "85" to 1, // Educacao
        "86" to 2, // Atividades de atencao a saude - NR-32
        "87" to 2, // Atividades de atencao a saude em residencias
        "88" to 2, // Servicos sociais sem alojamento
        "90" to 2, // Atividades artisticas
        "91" to 1, // Atividades de biblioteca e museu
        "92" to 2, // Atividades de jogo e apostas
        "93" to 2, // Atividades esportivas e recreativas
        "94" to 1, // Atividades de organizacoes associativas
        "95" to 2, // Reparacao de equipamentos
        "96" to 1, // Outras atividades de servicos pessoais
        "97" to 1  // Servicos domesticos
    )

    fun map(cnae: String?): Int {
        if (cnae.isNullOrBlank()) return 2
        val normalized = cnae.trim()

        exactMatches[normalized]?.let { return it }

        val prefix = normalized.filter { it.isDigit() }.take(2)
        prefixRules[prefix]?.let { return it }

        return 2
    }
}

