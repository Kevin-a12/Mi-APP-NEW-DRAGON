package mx.newdragon.pos

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

object Money {
    fun parse(text: String): Long {
        val value = text.trim().replace(',', '.')
        require(Regex("[0-9]{1,6}(\\.[0-9]{1,2})?").matches(value)) { "Escribe un precio válido con hasta 2 decimales." }
        return BigDecimal(value).movePointRight(2).longValueExact().also { require(it > 0) { "El precio debe ser mayor que cero." } }
    }
    fun show(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(BigDecimal.valueOf(cents, 2))
    fun editable(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}
object MenuSearch {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    fun matches(product: Product, query: String, category: String = "Todas"): Boolean {
        if (category != "Todas" && product.category != category) return false
        val haystack = normalize("${product.name} ${product.description} ${product.category}")
        return normalize(query).split(Regex("\\s+")).all { it in haystack }
    }
}
data class OptionGroup(val title: String, val count: Int, val options: List<String>,val disabled:Set<String> = emptySet())
fun optionGroups(json: String): List<OptionGroup> = JSONArray(json).let { array ->
    (0 until array.length()).map { i -> array.getJSONObject(i).let { obj ->
        val options = obj.getJSONArray("options")
        OptionGroup(obj.getString("title"), obj.optInt("count", 1), (0 until options.length()).map(options::getString),
            obj.optJSONArray("disabled")?.let { d -> (0 until d.length()).map(d::getString).toSet() } ?: emptySet())
    } }
}
data class OrderLine(
    val key: String = UUID.randomUUID().toString(), val productId: String, val variantId: String,
    val name: String, val category: String, val description: String, val variant: String,
    val unitCents: Long, val quantity: Int, val selections: String = "", val notes: String = ""
) {
    val total: Long get() = unitCents * quantity
    fun json() = JSONObject().put("key",key).put("productId",productId).put("variantId",variantId)
        .put("name",name).put("category",category).put("description",description).put("variant",variant)
        .put("unitCents",unitCents).put("quantity",quantity).put("selections",selections).put("notes",notes)
    companion object {
        fun from(j: JSONObject) = OrderLine(j.getString("key"),j.getString("productId"),j.getString("variantId"),
            j.getString("name"),j.getString("category"),j.getString("description"),j.getString("variant"),
            j.getLong("unitCents"),j.getInt("quantity"),j.optString("selections"),j.optString("notes"))
    }
}
data class OrderDraft(
    val clientId: String = UUID.randomUUID().toString(), val service: String = "Para llevar",
    val reference: String = "", val notes: String = "", val lines: List<OrderLine> = emptyList(),
    val deliveryAddress: String = "", val customerPhone: String = "", val payment: PaymentDraft = PaymentDraft(),val sourceTicketId:Long = 0
) {
    val total: Long get() = lines.sumOf { it.total }
    fun json() = JSONObject().put("draftFormat",2).put("sourceTicketId",sourceTicketId).put("clientId",clientId).put("service",service).put("reference",reference)
        .put("notes",notes).put("deliveryAddress",deliveryAddress).put("customerPhone",customerPhone).put("payment",payment.json())
        .put("lines",JSONArray().apply { lines.forEach { put(it.json()) } })
    companion object {
        fun from(raw: String): OrderDraft = JSONObject(raw).let { j ->
            val lines = j.getJSONArray("lines")
            OrderDraft(j.getString("clientId"),
                if(!j.has("draftFormat") && j.optString("deliveryAddress").isNotBlank()) "A domicilio" else j.getString("service"),j.getString("reference"),j.getString("notes"),
                (0 until lines.length()).map { OrderLine.from(lines.getJSONObject(it)) },
                if(j.has("deliveryAddress")) j.getString("deliveryAddress") else "",
                if(j.has("customerPhone")) j.getString("customerPhone") else "",
                if(j.has("payment")) PaymentDraft.from(j.getJSONObject("payment")) else PaymentDraft(),j.optLong("sourceTicketId",0))
        }
    }
}
