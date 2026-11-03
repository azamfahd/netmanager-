package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPackageProfitCalculation() {
    val stat = com.example.data.model.DetailedPackageProfitStat(
      profileName = "1 Hour - 1000MB",
      costPrice = 200.0,
      salePrice = 500.0,
      profitPerCard = 300.0,
      profitMarginPercent = 60.0,
      totalCardsGenerated = 50,
      soldCardsCount = 20,
      activeCardsCount = 5,
      unusedCardsCount = 25,
      expiredCardsCount = 5,
      totalRevenue = 10000.0,
      totalCost = 4000.0,
      netProfit = 6000.0
    )

    assertEquals(300.0, stat.profitPerCard, 0.001)
    assertEquals(60.0, stat.profitMarginPercent, 0.001)
    assertEquals(10000.0, stat.totalRevenue, 0.001)
    assertEquals(4000.0, stat.totalCost, 0.001)
    assertEquals(6000.0, stat.netProfit, 0.001)
  }
}
