package com.shoptracker.pos.data

data class ProductStat(
    val productId: Long,
    val name: String,
    val units: Int,
    val revenue: Long
)

data class DayStats(
    val units: Int,
    val gross: Long,
    val products: List<ProductStat>
) {
    /** Average selling price per unit in centavos, rounded half up. */
    val averagePrice: Long
        get() = if (units == 0) 0L else (gross + units / 2) / units
}

/** Only COMPLETED transactions count; UNDONE ones are ignored. */
fun computeStats(txns: List<SalesTransaction>): DayStats {
    val done = txns.filter { it.status == STATUS_COMPLETED }
    val products = done.groupBy { it.productId }.map { (id, list) ->
        ProductStat(
            productId = id,
            name = list.last().productName,
            units = list.sumOf { it.quantity },
            revenue = list.sumOf { it.totalCentavos }
        )
    }.sortedWith(compareByDescending<ProductStat> { it.units }.thenBy { it.name.lowercase() })
    return DayStats(
        units = products.sumOf { it.units },
        gross = products.sumOf { it.revenue },
        products = products
    )
}
