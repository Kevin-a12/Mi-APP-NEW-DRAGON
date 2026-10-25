package mx.newdragon.pos

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "products")
data class Product(@PrimaryKey val id: String, val category: String, val name: String, val description: String,
    val optionsJson: String = "[]", val active: Boolean = true, val sortOrder: Int = 0)
@Entity(tableName = "variants", foreignKeys = [ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("productId")])
data class Variant(@PrimaryKey val id: String, val productId: String, val label: String, val priceCents: Long, val sortOrder: Int = 0)
data class CatalogItem(@Embedded val product: Product, @Relation(parentColumn = "id", entityColumn = "productId") val variants: List<Variant>)
@Entity(tableName = "tickets", indices = [Index(value = ["clientId"], unique = true),Index("cashSessionId")])
data class Ticket(@PrimaryKey(autoGenerate = true) val id: Long = 0, val clientId: String, val createdAt: Long,
    val service: String, val reference: String, val notes: String, val totalCents: Long,
    @ColumnInfo(defaultValue = "''") val deliveryAddress: String = "",
    @ColumnInfo(defaultValue = "''") val customerPhone: String = "",
    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val paymentMethod: String = "UNSPECIFIED",
    @ColumnInfo(defaultValue = "0") val transferCents: Long = 0,
    @ColumnInfo(defaultValue = "0") val cashReceivedCents: Long = 0,
    @ColumnInfo(defaultValue = "'PAID'") val status:String = OrderStates.PAID,
    @ColumnInfo(defaultValue = "0") val paidAt:Long = 0,
    @ColumnInfo(defaultValue = "0") val deliveredAt:Long = 0,
    @ColumnInfo(defaultValue = "0") val voided:Boolean = false,
    @ColumnInfo(defaultValue = "0") val voidedAt:Long = 0,
    @ColumnInfo(defaultValue = "''") val voidReason:String = "",
    @ColumnInfo(defaultValue = "0") val cashSessionId:Long = 0) {
    @get:Ignore val payment: PaymentBreakdown get() = Payments.recorded(totalCents,PaymentMethod.decode(paymentMethod),transferCents,cashReceivedCents)
    @get:Ignore val folio: String get() = "ND-" + id.toString().padStart(6, '0')
    @get:Ignore val revenueTime:Long get()=if(paidAt>0) paidAt else createdAt
}
@Entity(tableName = "ticket_lines", foreignKeys = [ForeignKey(entity = Ticket::class, parentColumns = ["id"], childColumns = ["ticketId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("ticketId")])
data class TicketLine(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ticketId: Long,
    val productId: String, val variantId: String, val name: String, val category: String, val description: String,
    val variant: String, val unitCents: Long, val quantity: Int, val selections: String, val notes: String) {
    @get:Ignore val total: Long get() = unitCents * quantity
}
data class TicketDetail(@Embedded val ticket: Ticket, @Relation(parentColumn = "id", entityColumn = "ticketId") val lines: List<TicketLine>)
@Entity(tableName = "drafts") data class DraftRow(@PrimaryKey val id: Int = 1, val payload: String)
@Entity(tableName = "metadata") data class Metadata(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "print_attempts", indices = [Index("ticketId")])
data class PrintAttempt(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ticketId: Long, val timestamp: Long, val status: String, val detail: String)

@Dao
interface DragonDao {
    @Transaction @Query("SELECT * FROM products ORDER BY sortOrder, name") fun observeCatalog(): Flow<List<CatalogItem>>
    @Transaction @Query("SELECT * FROM products ORDER BY sortOrder, name") suspend fun allCatalog(): List<CatalogItem>
    @Query("SELECT * FROM tickets ORDER BY id DESC") fun observeTickets(): Flow<List<Ticket>>
    @Transaction @Query("SELECT * FROM tickets ORDER BY id") suspend fun allTickets(): List<TicketDetail>
    @Transaction @Query("SELECT * FROM tickets WHERE id = :id") suspend fun detail(id: Long): TicketDetail?
    @Transaction @Query("SELECT * FROM tickets WHERE clientId = :client") suspend fun ticketByClient(client: String): TicketDetail?
    @Query("SELECT * FROM drafts WHERE id = 1") suspend fun draft(): DraftRow?
    @Query("SELECT value FROM metadata WHERE `key` = :key") suspend fun meta(key: String): String?
    @Query("SELECT * FROM print_attempts ORDER BY id") suspend fun printAttempts(): List<PrintAttempt>
    @Query("SELECT * FROM print_attempts ORDER BY id DESC") fun observePrints():Flow<List<PrintAttempt>>
    @Query("SELECT * FROM cash_sessions ORDER BY id DESC") fun observeCashSessions():Flow<List<CashSession>>
    @Query("SELECT * FROM cash_sessions ORDER BY id") suspend fun cashSessions():List<CashSession>
    @Query("SELECT * FROM cash_sessions WHERE closedAt=0 LIMIT 1") suspend fun activeCashSession():CashSession?
    @Query("SELECT * FROM cash_movements ORDER BY id DESC") fun observeCashMovements():Flow<List<CashMovement>>
    @Query("SELECT * FROM cash_movements ORDER BY id") suspend fun cashMovements():List<CashMovement>
    @Upsert suspend fun putProduct(product: Product)
    @Upsert suspend fun putVariants(variants: List<Variant>)
    @Upsert suspend fun putDraft(draft: DraftRow)
    @Upsert suspend fun putMeta(meta: Metadata)
    @Insert suspend fun insertTicket(ticket: Ticket): Long
    @Update suspend fun updateTicket(ticket:Ticket)
    @Insert suspend fun insertCashSession(session:CashSession):Long
    @Update suspend fun updateCashSession(session:CashSession)
    @Insert suspend fun insertCashMovement(movement:CashMovement):Long
    @Insert suspend fun insertLines(lines: List<TicketLine>)
    @Insert suspend fun insertPrint(attempt: PrintAttempt)
    @Query("DELETE FROM ticket_lines WHERE ticketId = :ticketId") suspend fun deleteTicketLines(ticketId: Long)
    @Query("DELETE FROM print_attempts WHERE ticketId = :ticketId") suspend fun deleteTicketPrints(ticketId: Long)
    @Query("DELETE FROM tickets WHERE id = :ticketId") suspend fun deleteTicketRow(ticketId: Long): Int
    @Query("DELETE FROM print_attempts") suspend fun clearPrints()
    @Query("DELETE FROM ticket_lines") suspend fun clearLines()
    @Query("DELETE FROM tickets") suspend fun clearTickets()
    @Query("DELETE FROM variants") suspend fun clearVariants()
    @Query("DELETE FROM variants WHERE productId=:productId AND id NOT IN (:ids)") suspend fun removeVariantsExcept(productId:String,ids:List<String>)
    @Query("DELETE FROM cash_movements") suspend fun clearCashMovements()
    @Query("DELETE FROM cash_sessions") suspend fun clearCashSessions()
    @Query("DELETE FROM products") suspend fun clearProducts()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM metadata") suspend fun clearMetadata()
}
@Database(entities = [Product::class, Variant::class, Ticket::class, TicketLine::class, DraftRow::class, Metadata::class, PrintAttempt::class,CashSession::class,CashMovement::class], version = 4, exportSchema = true)
abstract class DragonDatabase : RoomDatabase() {
    abstract fun dao(): DragonDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep historical notes unchanged: they may contain preparation instructions.
                db.execSQL("ALTER TABLE tickets ADD COLUMN deliveryAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tickets ADD COLUMN customerPhone TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tickets ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                db.execSQL("ALTER TABLE tickets ADD COLUMN transferCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN cashReceivedCents INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4=object:Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tickets ADD COLUMN status TEXT NOT NULL DEFAULT 'PAID'")
                db.execSQL("ALTER TABLE tickets ADD COLUMN paidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN deliveredAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN voided INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN voidedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tickets ADD COLUMN voidReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tickets ADD COLUMN cashSessionId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tickets SET paidAt=createdAt")
                // The old receipt interpreted any address as delivery. Preserve that result.
                db.execSQL("UPDATE tickets SET service='A domicilio' WHERE trim(deliveryAddress)<>''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tickets_cashSessionId ON tickets(cashSessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cash_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, openedAt INTEGER NOT NULL, openingCents INTEGER NOT NULL, closedAt INTEGER NOT NULL, countedCents INTEGER NOT NULL, expectedCents INTEGER NOT NULL, differenceCents INTEGER NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cash_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, deltaCents INTEGER NOT NULL, concept TEXT NOT NULL, refundTicketId INTEGER, FOREIGN KEY(sessionId) REFERENCES cash_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_movements_sessionId ON cash_movements(sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_movements_refundTicketId ON cash_movements(refundTicketId)")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, DragonDatabase::class.java, "new_dragon.db")
            .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
    }
}
class DragonRepository(val db: DragonDatabase, private val context: Context) {
    val dao = db.dao()
    suspend fun ensureSeed() = db.withTransaction {
        if (dao.meta("menu_seed_v1") != null) return@withTransaction
        val seed = JSONArray(context.assets.open("menu_seed.json").bufferedReader().use { it.readText() })
        for (i in 0 until seed.length()) {
            val p = seed.getJSONObject(i)
            val id = p.getString("id")
            dao.putProduct(Product(id,p.getString("category"),p.getString("name"),p.getString("description"),
                p.getJSONArray("options").toString(),true,p.getInt("sortOrder")))
            val variants = p.getJSONArray("variants")
            dao.putVariants((0 until variants.length()).map { index -> variants.getJSONObject(index).let { v ->
                Variant(v.getString("id"),id,v.getString("label"),v.getLong("priceCents"),v.getInt("sortOrder"))
            } })
        }
        dao.putMeta(Metadata("menu_seed_v1","1"))
    }
    suspend fun readDraft(): OrderDraft = dao.draft()?.let { OrderDraft.from(it.payload) } ?: OrderDraft()
    suspend fun saveDraft(draft: OrderDraft) = dao.putDraft(DraftRow(payload=draft.json().toString()))
    suspend fun updateProduct(product: Product, variants: List<Variant>) = db.withTransaction {
        require(product.name.isNotBlank() && product.category.isNotBlank()) { "Nombre y categoría son obligatorios." }
        require(variants.isNotEmpty() && variants.all { it.productId==product.id && it.priceCents>0 && it.label.isNotBlank() }) { "Revisa las presentaciones y los precios." }
        require(variants.map {it.id}.distinct().size==variants.size && variants.map {MenuSearch.normalize(it.label)}.distinct().size==variants.size) {"No repitas nombres de presentaciones."}
        optionGroups(product.optionsJson).forEach {g ->
            require(g.title.isNotBlank() && g.count in 1..20 && g.options.isNotEmpty() && g.options.all {it.isNotBlank() && it.length<=200} && g.title.length<=100 && g.options.size<=100 && g.options.distinct().size==g.options.size) {"Revisa las opciones del platillo."}
            require(g.disabled.all {it in g.options} && (!product.active || g.options.any {it !in g.disabled})) {"Deja alguna opción disponible u oculta el platillo."}
        }
        dao.putProduct(product)
        dao.removeVariantsExcept(product.id,variants.map {it.id})
        dao.putVariants(variants)
    }
    suspend fun checkout(draft: OrderDraft, next: OrderDraft = OrderDraft(),paid:Boolean=true): TicketDetail = db.withTransaction {
        val existing=dao.ticketByClient(draft.clientId)
        if(existing!=null && draft.sourceTicketId==0L) return@withTransaction existing
        if(draft.sourceTicketId>0) require(existing?.ticket?.id==draft.sourceTicketId && !existing.ticket.voided && existing.ticket.status==OrderStates.PENDING) {"El pedido ya cambió. Ábrelo de nuevo desde Historial."}
        require(draft.lines.isNotEmpty()) { "El pedido está vacío." }
        require(draft.lines.all { it.quantity in 1..99 && it.unitCents in 1..99_999_999 && it.name.isNotBlank() }) { "Revisa cantidades y precios." }
        DeliveryRules.validate(draft)
        val payment=if(paid) draft.payment.resolve(draft.total) else Payments.recorded(draft.total,PaymentMethod.UNSPECIFIED,0,0)
        val now=System.currentTimeMillis()
        val session=if(paid) dao.activeCashSession()?.id ?: 0 else 0
        val record=Ticket(id=existing?.ticket?.id ?: 0,clientId=draft.clientId,createdAt=existing?.ticket?.createdAt ?: now,service=draft.service,
            reference=draft.reference,notes=draft.notes,totalCents=draft.total,
            deliveryAddress=if(draft.service=="A domicilio") draft.deliveryAddress.trim() else "",customerPhone=draft.customerPhone.trim(),
            paymentMethod=payment.method.name,transferCents=payment.transferCents,cashReceivedCents=payment.cashReceivedCents,
            status=if(paid) OrderStates.PAID else OrderStates.PENDING,paidAt=if(paid) now else 0,cashSessionId=session)
        val id=if(existing!=null) {dao.updateTicket(record);dao.deleteTicketLines(record.id);record.id} else dao.insertTicket(record)
        dao.insertLines(draft.lines.map { l -> TicketLine(ticketId=id,productId=l.productId,variantId=l.variantId,name=l.name,
            category=l.category,description=l.description,variant=l.variant,unitCents=l.unitCents,quantity=l.quantity,selections=l.selections,notes=l.notes) })
        saveDraft(next)
        checkNotNull(dao.detail(id))
    }
    suspend fun voidTicket(ticketId:Long,reason:String,refundCash:Boolean=false)=db.withTransaction {
        require(reason.trim().length in 3..300) {"Escribe el motivo de la anulación (al menos 3 caracteres)."}
        val ticket=checkNotNull(dao.detail(ticketId)) {"El ticket ya no existe."}.ticket
        check(!ticket.voided) {"El ticket ya está anulado."}
        val now=System.currentTimeMillis()
        if(refundCash) {
            val cash=ticket.payment.cashAppliedCents
            require(cash>0) {"Este pedido no recibió efectivo."}
            val session=checkNotNull(dao.activeCashSession()) {"Abre la caja para registrar la devolución."}
            dao.insertCashMovement(CashMovement(sessionId=session.id,timestamp=now,deltaCents=-cash,concept="Devolución ${ticket.folio}",refundTicketId=ticket.id))
        }
        dao.updateTicket(ticket.copy(voided=true,voidedAt=now,voidReason=reason.trim()))
        if(readDraft().sourceTicketId==ticket.id) saveDraft(OrderDraft())
    }
    suspend fun markDelivered(id:Long)=db.withTransaction {
        val t=checkNotNull(dao.detail(id)).ticket
        require(!t.voided && t.status==OrderStates.PAID) {"Primero registra el pago del pedido."}
        dao.updateTicket(t.copy(status=OrderStates.DELIVERED,deliveredAt=System.currentTimeMillis()))
    }
    suspend fun openCash(opening:Long)=db.withTransaction {
        require(opening in 0..Payments.MAX_CENTS) {"Fondo de caja inválido."}
        check(dao.activeCashSession()==null) {"Ya hay una caja abierta."}
        dao.insertCashSession(CashSession(openedAt=System.currentTimeMillis(),openingCents=opening))
    }
    suspend fun cashMovement(delta:Long,concept:String)=db.withTransaction {
        require(delta!=0L && delta in -Payments.MAX_CENTS..Payments.MAX_CENTS && concept.trim().length in 3..300) {"Revisa el monto y el concepto."}
        val session=checkNotNull(dao.activeCashSession()) {"Primero abre la caja."}
        dao.insertCashMovement(CashMovement(sessionId=session.id,timestamp=System.currentTimeMillis(),deltaCents=delta,concept=concept.trim()))
    }
    suspend fun closeCash(counted:Long,notes:String=""):CashSession=db.withTransaction {
        require(counted in 0..Payments.MAX_CENTS && notes.length<=300) {"Revisa el efectivo contado."}
        val session=checkNotNull(dao.activeCashSession()) {"No hay una caja abierta."}
        val expected=CashSummary.create(session,dao.allTickets().map {it.ticket},dao.cashMovements()).expectedCents
        session.copy(closedAt=System.currentTimeMillis(),countedCents=counted,expectedCents=expected,differenceCents=counted-expected,notes=notes.trim()).also {dao.updateCashSession(it)}
    }
    private fun lastTicketId(): Long = db.openHelper.writableDatabase
        .query("SELECT seq FROM sqlite_sequence WHERE name = 'tickets'").use { if(it.moveToFirst()) it.getLong(0) else 0L }

    suspend fun restoreBackup(backup: BackupSnapshot) = db.withTransaction {
        val sequence=maxOf(lastTicketId(),backup.lastTicketId)
        dao.clearPrints(); dao.clearLines(); dao.clearTickets()
        dao.clearCashMovements();dao.clearCashSessions()
        dao.clearVariants(); dao.clearProducts(); dao.clearDrafts(); dao.clearMetadata()
        backup.catalog.forEach {dao.putProduct(it.product);dao.putVariants(it.variants)}
        backup.tickets.forEach {dao.insertTicket(it.ticket);dao.insertLines(it.lines)}
        backup.printAttempts.forEach {dao.insertPrint(it)}
        backup.cashSessions.forEach {dao.insertCashSession(it)}
        backup.cashMovements.forEach {dao.insertCashMovement(it)}
        saveDraft(backup.draft)
        dao.putMeta(Metadata("menu_seed_v1","1"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name = 'tickets'")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES('tickets', ?)",arrayOf(sequence))
    }
    suspend fun exportJson(): String = db.withTransaction {
        JSONObject().put("format","new-dragon-backup").put("version",5).put("currency","MXN").put("lastTicketId",lastTicketId())
            .put("exportedAt",System.currentTimeMillis()).put("draft",readDraft().json())
            .put("catalog",JSONArray().apply { dao.allCatalog().forEach { c ->
                put(JSONObject().put("id",c.product.id).put("name",c.product.name).put("category",c.product.category)
                    .put("description",c.product.description).put("active",c.product.active).put("sortOrder",c.product.sortOrder)
                    .put("options",JSONArray(c.product.optionsJson)).put("variants",JSONArray().apply { c.variants.forEach { v ->
                        put(JSONObject().put("id",v.id).put("label",v.label).put("priceCents",v.priceCents).put("sortOrder",v.sortOrder))
                    } }))
            } }).put("tickets",JSONArray().apply { dao.allTickets().forEach { t ->
                put(JSONObject().put("id",t.ticket.id).put("clientId",t.ticket.clientId).put("createdAt",t.ticket.createdAt)
                    .put("service",t.ticket.service).put("reference",t.ticket.reference).put("notes",t.ticket.notes).put("totalCents",t.ticket.totalCents)
                    .put("deliveryAddress",t.ticket.deliveryAddress).put("customerPhone",t.ticket.customerPhone)
                    .put("paymentMethod",t.ticket.paymentMethod).put("transferCents",t.ticket.transferCents).put("cashReceivedCents",t.ticket.cashReceivedCents)
                    .put("status",t.ticket.status).put("paidAt",t.ticket.paidAt).put("deliveredAt",t.ticket.deliveredAt)
                    .put("voided",t.ticket.voided).put("voidedAt",t.ticket.voidedAt).put("voidReason",t.ticket.voidReason).put("cashSessionId",t.ticket.cashSessionId)
                    .put("lines",JSONArray().apply { t.lines.sortedBy { it.id }.forEach { l ->
                        put(OrderLine(l.id.toString(),l.productId,l.variantId,l.name,l.category,l.description,l.variant,l.unitCents,l.quantity,l.selections,l.notes).json())
                    } }))
            } }).put("printAttempts",JSONArray().apply { dao.printAttempts().forEach { p ->
                put(JSONObject().put("id",p.id).put("ticketId",p.ticketId).put("timestamp",p.timestamp).put("status",p.status).put("detail",p.detail))
            } }).put("cashSessions",JSONArray().apply {dao.cashSessions().forEach {s -> put(JSONObject().put("id",s.id).put("openedAt",s.openedAt).put("openingCents",s.openingCents).put("closedAt",s.closedAt).put("countedCents",s.countedCents).put("expectedCents",s.expectedCents).put("differenceCents",s.differenceCents).put("notes",s.notes))}})
            .put("cashMovements",JSONArray().apply {dao.cashMovements().forEach {m -> put(JSONObject().put("id",m.id).put("sessionId",m.sessionId).put("timestamp",m.timestamp).put("deltaCents",m.deltaCents).put("concept",m.concept).put("refundTicketId",m.refundTicketId ?: JSONObject.NULL))}}).toString(2)
    }
}
