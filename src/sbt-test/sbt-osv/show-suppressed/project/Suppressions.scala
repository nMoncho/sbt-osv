import net.nmoncho.sbt.osv.SuppressionRule
import net.nmoncho.sbt.osv.settings.SuppressionSettings

// Suppression scenarios, referenced from `test` through `set`
object Suppressions {

  private val used = SuppressionRule("GHSA-p66x-2cv9-qq3v", "matches a found vulnerability", "build")

  val one: SuppressionSettings = SuppressionSettings(suppressions = Set(used))
}
