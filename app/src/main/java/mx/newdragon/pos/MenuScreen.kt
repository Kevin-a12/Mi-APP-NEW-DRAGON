package mx.newdragon.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable fun MenuScreen(vm:DragonViewModel,catalog:List<CatalogItem>) {
    var query by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Editar menú",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Button({vm.creatingMenu=true;vm.menuItem=null}) {Text("Añadir platillo")}
        }
        Text("Los tickets guardados conservan sus precios y opciones.",style=MaterialTheme.typography.bodySmall,color=DragonMuted)
        OutlinedTextField(query,{query=it},label={Text("Buscar en el menú")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(catalog.filter {MenuSearch.matches(it.product,query)},key={it.product.id}) {item ->
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.product.name,fontWeight=FontWeight.Bold)
                            Text("${item.product.category} · ${if(item.product.active) "Disponible" else "Oculto"}",style=MaterialTheme.typography.bodySmall)
                            Text(item.variants.sortedBy {it.sortOrder}.joinToString(" / ") {"${it.label}: ${Money.show(it.priceCents)}"},style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton({vm.menuItem=item;vm.creatingMenu=false}) {Text("Editar")}
                    }
                }
            }
        }
    }
    if(vm.creatingMenu || vm.menuItem!=null) ProductEditor(vm.menuItem,catalog.map {it.product.category}.distinct().sorted(),vm.menuSaving,
        onDismiss={vm.creatingMenu=false;vm.menuItem=null},onSave={p,v -> vm.saveProduct(p,v) {vm.creatingMenu=false;vm.menuItem=null}})
}
private data class VariantEdit(val id:String,val label:String,val price:String)
private fun variantsFrom(raw:String)=JSONArray(raw).let {a -> (0 until a.length()).map {i -> a.getJSONObject(i).let {VariantEdit(it.getString("id"),it.getString("label"),it.getString("price"))}}}
private fun variantsTo(values:List<VariantEdit>)=JSONArray().apply {values.forEach {v -> put(JSONObject().put("id",v.id).put("label",v.label).put("price",v.price))}}.toString()
@Composable fun ProductEditor(item:CatalogItem?,categories:List<String>,busy:Boolean,onDismiss:()->Unit,onSave:(Product,List<Variant>)->Unit) {
    val initialVariants=rememberSaveable(item?.product?.id) {variantsTo(item?.variants?.sortedBy {it.sortOrder}?.map {VariantEdit(it.id,it.label,Money.editable(it.priceCents))} ?: listOf(VariantEdit(UUID.randomUUID().toString(),"Normal","")))}
    val initialGroups=remember(item) {optionsJson(optionGroups(item?.product?.optionsJson ?: "[]"))}
    var id by rememberSaveable {mutableStateOf(item?.product?.id ?: UUID.randomUUID().toString())}
    var name by rememberSaveable {mutableStateOf(item?.product?.name ?: "")}
    var category by rememberSaveable {mutableStateOf(item?.product?.category ?: "")}
    var newCategory by rememberSaveable {mutableStateOf(false)}
    var description by rememberSaveable {mutableStateOf(item?.product?.description ?: "")}
    var active by rememberSaveable {mutableStateOf(item?.product?.active ?: true)}
    var variantJson by rememberSaveable {mutableStateOf(initialVariants)}
    var groupJson by rememberSaveable {mutableStateOf(initialGroups)}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var closeConfirm by rememberSaveable {mutableStateOf(false)}
    val variants=variantsFrom(variantJson)
    val groups=optionGroups(groupJson)
    val dirty=name!=(item?.product?.name ?: "") || category!=(item?.product?.category ?: "") || description!=(item?.product?.description ?: "") || active!=(item?.product?.active ?: true) || variantJson!=initialVariants || groupJson!=initialGroups
    fun close() {if(!busy) {if(dirty) closeConfirm=true else onDismiss()}}
    fun variant(index:Int,transform:(VariantEdit)->VariantEdit) {variantJson=variantsTo(variants.mapIndexed {i,v -> if(i==index) transform(v) else v})}
    fun group(index:Int,transform:(OptionGroup)->OptionGroup) {groupJson=optionsJson(groups.mapIndexed {i,g -> if(i==index) transform(g) else g})}
    AlertDialog(onDismissRequest=::close,title={Text(if(item==null) "Nuevo platillo" else "Editar platillo")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,{name=it.take(100)},label={Text("Nombre *")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            Choice("Categoría",category.ifBlank {"Seleccionar"},categories+"Nueva categoría…",!busy) {value -> if(value=="Nueva categoría…") {category="";newCategory=true} else {category=value;newCategory=false}}
            if(newCategory) OutlinedTextField(category,{category=it.take(60)},label={Text("Nombre de la categoría")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(description,{description=it.take(600)},label={Text("Descripción para el ticket")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().toggleable(value=active,enabled=!busy,role=Role.Switch,onValueChange={active=it}),verticalAlignment=Alignment.CenterVertically) {Switch(active,null,enabled=!busy);Text("Disponible para pedidos",Modifier.padding(start=8.dp))}
            Text("Presentaciones y precios",fontWeight=FontWeight.Bold)
            variants.forEachIndexed {index,v ->
                Card(colors=CardDefaults.cardColors(containerColor=DragonCream)) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(v.label,{value -> variant(index) {it.copy(label=value.take(60))}},label={Text("Presentación ${index+1}")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(v.price,{value -> variant(index) {it.copy(price=value.take(10))}},label={Text("Precio MXN")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!busy,modifier=Modifier.fillMaxWidth())
                        TextButton({variantJson=variantsTo(variants.filterIndexed {i,_ -> i!=index})},enabled=!busy && variants.size>1) {Text("Quitar presentación")}
                    }
                }
            }
            TextButton({variantJson=variantsTo(variants+VariantEdit(UUID.randomUUID().toString(),"",""))},enabled=!busy && variants.size<10) {Text("Añadir presentación")}
            HorizontalDivider()
            Text("Guisos y opciones",fontWeight=FontWeight.Bold)
            Text("Una opción por línea. Desmarca lo que no esté disponible. El cliente puede repetir opciones.",style=MaterialTheme.typography.bodySmall)
            groups.forEachIndexed {index,g ->
                Card(colors=CardDefaults.cardColors(containerColor=DragonCream)) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(g.title,{value -> group(index) {it.copy(title=value.take(100))}},label={Text("Nombre del grupo")},placeholder={Text("Ej. Guiso")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                        Choice("Cuántas debe elegir",g.count.toString(),(1..20).map {it.toString()},!busy) {value -> group(index) {it.copy(count=value.toInt())}}
                        OutlinedTextField(g.options.joinToString("\n"),{value -> val names=value.take(12000).split('\n');group(index) {it.copy(options=names,disabled=it.disabled.intersect(names.toSet()))}},label={Text("Opciones, una por línea")},enabled=!busy,minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth())
                        g.options.filter {it.isNotBlank()}.distinct().forEach {option -> Row(Modifier.fillMaxWidth().toggleable(value=option !in g.disabled,enabled=!busy,role=Role.Checkbox,onValueChange={available -> group(index) {it.copy(disabled=if(available) it.disabled-option else it.disabled+option)}}),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(option !in g.disabled,null,enabled=!busy)
                            Text(option,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        }}
                        TextButton({groupJson=optionsJson(groups.filterIndexed {i,_ -> i!=index})},enabled=!busy) {Text("Quitar grupo de opciones")}
                    }
                }
            }
            TextButton({groupJson=optionsJson(groups+OptionGroup("",1,listOf("")))},enabled=!busy && groups.size<20) {Text("Añadir grupo de opciones")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },dismissButton={TextButton(::close,enabled=!busy) {Text("Cancelar")}},confirmButton={Button({
        runCatching {
            require(name.isNotBlank() && category.isNotBlank()) {"Completa nombre y categoría."}
            val normalizedCategory=categories.firstOrNull {MenuSearch.normalize(it)==MenuSearch.normalize(category)} ?: category.trim()
            val parsed=variants.mapIndexed {index,v -> require(v.label.isNotBlank()) {"Completa cada presentación o quítala."};Variant(v.id,id,v.label.trim(),Money.parse(v.price),index)}
            require(parsed.map {MenuSearch.normalize(it.label)}.distinct().size==parsed.size) {"Usa nombres distintos para las presentaciones."}
            val normalizedGroups=groups.map {g ->
                val values=g.options.map {it.trim()}.filter {it.isNotBlank()}
                require(g.title.isNotBlank() && values.isNotEmpty() && values.size<=100 && values.all {it.length<=200} && values.distinct().size==values.size) {"Revisa los grupos y sus opciones."}
                val disabled=g.disabled.map {it.trim()}.toSet().intersect(values.toSet())
                require(!active || values.any {it !in disabled}) {"Deja alguna opción disponible u oculta el platillo."}
                g.copy(title=g.title.trim(),options=values,disabled=disabled)
            }
            onSave(Product(id,normalizedCategory,name.trim(),description.trim(),optionsJson(normalizedGroups),active,item?.product?.sortOrder ?: 10000),parsed)
        }.onFailure {error=it.message}
    },enabled=!busy) {Text(if(busy) "Guardando…" else "Guardar cambios")}})
    DiscardPrompt(closeConfirm,{closeConfirm=false},onDismiss)
}
