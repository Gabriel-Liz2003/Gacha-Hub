package dev.gachahub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.gachahub.R
import dev.gachahub.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun CatalogImage(url: String, name: String, size: Int = 76) {
    AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(url).size(size*3).crossfade(true).build(),
        contentDescription=name,modifier=Modifier.size(size.dp),contentScale=ContentScale.Fit,
        placeholder=painterResource(R.drawable.ic_hub),error=painterResource(R.drawable.ic_hub),fallback=painterResource(R.drawable.ic_hub))
}

@Composable internal fun Choice(label: String, selected: String, choices: List<Pair<String,String>>, change: (String)->Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().testTag("choice:$label")) {
        Text("$label: ${choices.find { it.first == selected }?.second ?: selected.ifEmpty { "Todos" }}")
    }
    if(open) {
        var query by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest={open=false},title={Text(label)},text={Column {
            Field("Buscar opção",query,{query=it})
            val options=choices.filter { searchKey(it.second).contains(searchKey(query)) }
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                if(options.isEmpty())item { Text("Nenhuma opção encontrada.") }
                items(options,key={it.first}) { (id,name) -> TextButton(onClick={change(id);open=false},modifier=Modifier.fillMaxWidth()) { Text(name) } }
            }
        }},confirmButton={TextButton(onClick={open=false}) { Text("Fechar") }})
    }
}

@Composable internal fun EquipmentDetails(weapon: Weapon?, disc: DiscSet?, close:()->Unit) {
    if(weapon==null && disc==null)return
    AlertDialog(onDismissRequest=close,title={Text(weapon?.name ?: disc!!.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        CatalogImage(weapon?.image ?: disc!!.image,weapon?.name ?: disc!!.name,100)
        weapon?.let { w ->
            Text("${when(w.rarity){5->"S";4->"A";else->"B"}} • ${w.specialty}")
            Text(w.statContext);w.baseStats.forEach { (stat,value)->Text("$stat: $value") };Text(w.effect)
            w.sources.forEach { SourceView(it) }
        }
        disc?.let { d -> Text("2 peças: ${d.twoPiece}");Text("4 peças: ${d.fourPiece}");d.sources.forEach { SourceView(it) } }
    }},confirmButton={TextButton(onClick=close){Text("Fechar equipamento")}})
}

@Composable internal fun BuildEquipment(build: Build, pack: ContentPack?) {
    var weaponId by remember { mutableStateOf<String?>(null) }
    var discId by remember { mutableStateOf<String?>(null) }
    val weapons=remember(pack){pack?.weapons.orEmpty().associateBy{it.id}}
    val discs=remember(pack){pack?.discSets.orEmpty().associateBy{it.id}}
    build.weaponOptions.forEach { option ->
        val w=weapons[option.weaponId]
        TextButton(onClick={weaponId=option.weaponId}) {
            Text("${when(option.tier){"bis"->"Recomendado";"premium"->"Premium / pago";"accessible"->"Acessível / F2P";else->"Alternativa"}}: ${w?.name ?: option.weaponId}")
        }
        if(option.note.isNotBlank())Text(option.note,style=MaterialTheme.typography.bodySmall)
    }
    build.discOptions.forEachIndexed { i,option ->
        Text(if(i==0)"Drive Discs recomendados" else "Alternativa de discos")
        TextButton(onClick={discId=option.fourPieceId}){Text("4p ${discs[option.fourPieceId]?.name}")}
        Text("Combine com UM dos conjuntos:")
        option.twoPieceIds.forEach { id->TextButton(onClick={discId=id}){Text("2p ${discs[id]?.name}")} }
        Text(option.note,style=MaterialTheme.typography.bodySmall)
    }
    if(weaponId!=null || discId!=null) EquipmentDetails(weapons[weaponId],discs[discId]){weaponId=null;discId=null}
}

@Composable internal fun CharacterDetails(character: Character, state: HubState, account: Account?, vm: HubViewModel, close:()->Unit) {
    val pack=state.pack
    var tab by rememberSaveable(character.id){mutableStateOf("Ficha")}
    var equipment by remember{mutableStateOf<String?>(null)}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=close){Text("‹ Voltar ao catálogo")}
                Text(character.name,style=MaterialTheme.typography.titleLarge)
            }
            Row { listOf("Ficha","Build","Evolução","Times").forEach { name -> TextButton(onClick={tab=name}){Text(if(tab==name)"● $name" else name)} } }
            LazyColumn(Modifier.weight(1f).testTag("agent-details"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(tab) {
                    "Ficha" -> {
                        item { CatalogImage(character.image,character.name,116);Text(character.fullName.ifBlank{character.name});Text("${if(character.rarity==5)"S" else "A"} • ${character.element} ${character.attributeVariant} • ${character.specialty}");Text(character.faction);Text(character.description) }
                        item { Section("Atributos base") { Text(character.statContext);character.baseStats.forEach{(name,n)->Text("$name: $n")} } }
                        item { Section("W-Engine assinatura") {TextButton(onClick={equipment=character.signatureWeaponId}) {Text(pack?.weapons?.find{it.id==character.signatureWeaponId}?.name ?: "—")} } }
                        item { Section("Habilidades") {character.skillNames.forEach{(key,names)->Text(Zzz.labels["skill:$key"] ?: key);names.forEach{Text("• $it")}};Text(character.additionalAbility) } }
                        item { Section("Mindscape Cinema") {character.mindscapes.forEach{Text(it)};Text("Os níveis de habilidades registrados são os níveis base, sem os bônus de M3/M5.")} }
                        item {character.sources.forEach{SourceView(it)}}
                    }
                    "Build" -> items(pack?.builds.orEmpty().filter{it.characterId==character.id},key={it.id}) { build -> BuildCard(build,Game.ZZZ,account,state,vm) }
                    "Evolução" -> {
                        item { Text("Nível 1–60 • promoção 0–5 • Núcleo 0/A–F • cinco habilidades de nível base 1–12.")
                            Text("As trilhas são independentes. Crie um projeto na seção Planejamento; o inventário é compartilhado entre os projetos da conta.")
                            Text("EXP é contabilizada em pontos, permitindo combinar itens de raridades diferentes.") }
                        val steps=pack?.costs.orEmpty().filter{it.characterId==character.id}
                        items(steps.groupBy{it.track}.toList(),key={it.first}) { (track,edges)->Section(Zzz.trackLabel(track,pack)) {
                            Text("${edges.minOf{it.from}} → ${edges.maxOf{it.to}}")
                            val totals=remember(edges){dev.gachahub.core.ResourceMath.sum(edges.map{it.costs})}
                            totals.forEach{(id,n)->Text("${state.materials.find{it.id==id}?.name ?: id}: $n")}
                        } }
                    }
                    "Times" -> items(pack?.teams.orEmpty().filter{it.game==Game.ZZZ && it.slots.flatten().contains(character.id)},key={it.id}) { guide -> Section(guide.name) {
                        guide.slots.forEachIndexed { i,slot->Text("${i+1}: ${slot.joinToString(" / "){id->state.characters.find{it.id==id}?.name ?: id}}") }
                        Text(guide.explanation)
                        val team=Planner.availableTeam(guide,account?.characters.orEmpty().map{it.characterId}.toSet())
                        Text(if(team==null)"A conta ainda não tem uma combinação completa." else "Disponível: ${team.joinToString(" • "){id->state.characters.find{it.id==id}?.name ?: id}}")
                        guide.source.let{SourceView(it)}
                    } }
                }
            }
        } }
    }
    equipment?.let { id->EquipmentDetails(pack?.weapons?.find{it.id==id},null){equipment=null} }
}

@Composable internal fun EquipmentPage(game: Game, state: HubState) {
    var query by rememberSaveable(game){mutableStateOf("")}
    var type by rememberSaveable(game){mutableStateOf("W-Engines")}
    var selectedWeapon by remember{mutableStateOf<Weapon?>(null)}
    var selectedDisc by remember{mutableStateOf<DiscSet?>(null)}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Field("Buscar equipamento",query,{query=it});Row {listOf("W-Engines","Drive Discs").forEach { FilterChip(type==it,{type=it},label={Text(it)}) } } }
        if(type=="W-Engines") {
            val weapons=state.pack?.weapons.orEmpty().filter{it.game==game && searchKey(it.name+" "+it.specialty).contains(searchKey(query))}
            if(weapons.isEmpty())item{Text("Nenhum W-Engine encontrado.")}
            items(weapons,key={it.id}) { w->Card(onClick={selectedWeapon=w},modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(12.dp)) {CatalogImage(w.image,w.name);Column {Text(w.name);Text(w.specialty)}}} }
        } else {
            val discs=state.pack?.discSets.orEmpty().filter{it.game==game && searchKey(it.name).contains(searchKey(query))}
            if(discs.isEmpty())item{Text("Nenhum conjunto encontrado.")}
            items(discs,key={it.id}) { d->Card(onClick={selectedDisc=d},modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(12.dp)) {CatalogImage(d.image,d.name);Column {Text(d.name);Text("2p: ${d.twoPiece}")}}} }
        }
    }
    if(selectedWeapon!=null || selectedDisc!=null)EquipmentDetails(selectedWeapon,selectedDisc){selectedWeapon=null;selectedDisc=null}
}

@Composable internal fun ZzzOwnedEditor(character: Character, pack: ContentPack?, initial: Owned, close:()->Unit, save:(Owned)->Unit) {
    var level by rememberSaveable {mutableStateOf(initial.level.toString())}
    var asc by rememberSaveable{mutableStateOf(initial.ascension.toString())}
    var copies by rememberSaveable{mutableStateOf(initial.copies.toString())}
    var notes by rememberSaveable{mutableStateOf(initial.notes)}
    val skills=remember{mutableStateMapOf<String,String>().apply{Zzz.skillKeys.forEach{put(it,(initial.skills[it] ?: 1).toString())};put("core",(initial.skills["core"] ?: 0).toString())}}
    val stats=remember{mutableStateMapOf<String,String>().apply{initial.stats.forEach{(k,v)->put(k,v.toString())}}}
    var weaponId by rememberSaveable {mutableStateOf(initial.weapon?.id.orEmpty())}
    var weaponLevel by rememberSaveable {mutableStateOf((initial.weapon?.level ?: 1).toString())}
    var weaponAsc by rememberSaveable {mutableStateOf((initial.weapon?.ascension ?: 0).toString())}
    var refinement by rememberSaveable {mutableStateOf((initial.weapon?.refinement ?: 1).toString())}
    var discs by remember {mutableStateOf(initial.equipment)}
    var error by remember{mutableStateOf<String?>(null)}
    var section by rememberSaveable{mutableStateOf("Progresso")}
    AlertDialog(onDismissRequest=close,title={Text(character.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Choice("Editar",section,listOf("Progresso","Atributos","W-Engine","Drive Discs").map{it to it}){section=it}
        when(section) {
            "Progresso" -> {
                Field("Nível (1–60)",level,{level=it},true);Field("Promoção (0–5)",asc,{asc=it},true);Field("Mindscape Cinema (0–6)",copies,{copies=it},true)
                Text("Informe níveis base; os bônus de M3/M5 não entram no planejamento.")
                (Zzz.skillKeys+"core").forEach{key->Field(Zzz.labels[if(key=="core")key else "skill:$key"] ?: key,skills[key].orEmpty(),{skills[key]=it},true)}
                Field("Observações",notes,{notes=it})
            }
            "Atributos" -> {
                Text("Valores da tela fora de combate. Porcentagem: 70, não 0,70.")
                (listOf("ATK","HP","DEF","CRIT Rate","CRIT DMG","PEN Ratio","Anomaly Proficiency","Anomaly Mastery","Impact","Energy Regen")+initial.stats.keys).distinct().forEach{key->Field(key,stats[key].orEmpty(),{stats[key]=it},true)}
            }
            "W-Engine" -> {
                val choices=listOf("" to "Sem W-Engine")+pack?.weapons.orEmpty().map{it.id to "${it.name} • ${it.specialty}"}
                Choice("W-Engine",weaponId,choices){weaponId=it}
                if(weaponId.isNotBlank()) {Field("Nível do W-Engine",weaponLevel,{weaponLevel=it},true);Field("Promoção do W-Engine (0–5)",weaponAsc,{weaponAsc=it},true);Field("Fase (1–5)",refinement,{refinement=it},true)}
            }
            "Drive Discs" -> (1..6).forEach { slot ->
                val gear=discs.find{it.slot==slot.toString()} ?: Gear(slot=slot.toString(),level=0)
                fun update(g: Gear){discs=discs.filterNot{it.slot==g.slot}+g}
                Choice("Disco $slot",gear.set,listOf("" to "Sem disco")+pack?.discSets.orEmpty().map{it.id to it.name}){id->
                    if(id.isBlank())discs=discs.filterNot{it.slot==slot.toString()} else update(gear.copy(set=id,id=id+":$slot",name=pack?.discSets?.find{it.id==id}?.name.orEmpty()))
                }
                if(gear.set.isNotBlank()) {
                    var diskLevel by remember(gear.id){mutableStateOf(gear.level.toString())}
                    Field("Nível do disco $slot (0–15)",diskLevel,{diskLevel=it;update(gear.copy(level=it.toIntOrNull() ?: -1))},true)
                    Choice("Principal do disco $slot",gear.main,discMainStats(slot).map{it to it}){update(gear.copy(main=it))}
                }
            }
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={try {
        val w=pack?.weapons?.find{it.id==weaponId}
        val owned=initial.copy(level=level.toInt(),ascension=asc.toInt(),copies=copies.toInt(),notes=notes,
            skills=skills.mapValues{it.value.toInt()},stats=stats.filterValues{it.isNotBlank()}.mapValues{it.value.replace(',','.').toDouble()},
            weapon=if(weaponId.isBlank())null else (initial.weapon ?: Gear()).copy(id=weaponId,name=w?.name ?: initial.weapon?.name.orEmpty(),level=weaponLevel.toInt(),refinement=refinement.toInt(),ascension=weaponAsc.toInt()),equipment=discs)
        owned.validate(Game.ZZZ);save(owned)
    }catch(e:Exception){error=e.message ?: "Confira os valores"}}){Text("Salvar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
private fun discMainStats(slot:Int)=when(slot){1->listOf("HP");2->listOf("ATK");3->listOf("DEF");4->listOf("HP%","ATK%","DEF%","CRIT Rate","CRIT DMG","Anomaly Proficiency");5->listOf("HP%","ATK%","DEF%","PEN Ratio","Physical DMG","Fire DMG","Ice DMG","Electric DMG","Ether DMG","Wind DMG","Lumiflux DMG");else->listOf("HP%","ATK%","DEF%","Anomaly Mastery","Impact","Energy Regen")}
