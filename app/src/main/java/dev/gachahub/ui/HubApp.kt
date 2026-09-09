package dev.gachahub.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.gachahub.core.ResourceMath
import dev.gachahub.data.*
import dev.gachahub.data.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun newId() = UUID.randomUUID().toString()
private val pages = listOf("Resumo","Conta","Personagens","Builds","Planejamento","Times","Materiais","Calendário","Favoritos")

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HubApp(vm: HubViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var gameName by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var globalPlanning by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("Resumo") }
    val game = gameName?.let(Game::valueOf)
    val account = state.accounts.find { it.id == accountId && it.game == game } ?: state.accounts.firstOrNull { it.game == game }
    var fileKind by rememberSaveable { mutableStateOf("import") }
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) vm.perform {
            val text = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded() } ?: error("Arquivo indisponível")
                require(bytes.size <= 8*1024*1024) { "Arquivo excede 8 MB" }
                bytes.toString(Charsets.UTF_8)
            }
            vm.readFile(text, importTarget, fileKind)
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) vm.perform("Backup exportado") {
            val text = vm.repository.export()
            withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) } }
        }
    }
    fun openFile(kind: String) { fileKind = kind; importTarget=account?.id; open.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
    val accent = Color(game?.accent ?: 0xFFB6EF66)
    MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=Color(0xFF101217),surface=Color(0xFF191D25),surfaceVariant=Color(0xFF272D37))) {
        BackHandler(game != null || globalPlanning) { gameName = null; globalPlanning = false; page = "Resumo" }
        Scaffold(topBar={ TopAppBar(title={ Column {
            Text(game?.title ?: if(globalPlanning) "Planejamentos" else "Gacha Hub", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
            Text(account?.name?.takeIf { game != null } ?: "Sua coleção. Seu próximo objetivo.",style=MaterialTheme.typography.labelMedium)
        } },navigationIcon={ if(game != null || globalPlanning) TextButton(onClick={gameName=null;globalPlanning=false}) { Text("‹") } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(globalPlanning) {
                    GlobalPlanningPage(state) { project ->
                        val target = state.accounts.first { it.id == project.accountId }
                        globalPlanning=false; gameName=target.game.name; accountId=target.id; page="Planejamento"
                    }
                } else if(game == null) {
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        item { Text("QUATRO MUNDOS, UM HUB",style=MaterialTheme.typography.labelLarge,color=accent) }
                        item { OutlinedButton(onClick={globalPlanning=true},modifier=Modifier.fillMaxWidth()) {
                            Text("Todos os planejamentos (${state.projects.count { !it.completed }})")
                        } }
                        items(Game.entries) { g ->
                            Card(onClick={ gameName=g.name; accountId=state.accounts.firstOrNull { it.game==g }?.id; page="Resumo" },modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(22.dp)) {
                                    Text(g.name,color=Color(g.accent),style=MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(14.dp))
                                    Text(g.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    Text("${state.accounts.count { it.game==g }} contas • ${state.characters.count { it.game==g }} no catálogo local")
                                }
                            }
                        }
                        item { Section("Conteúdo e privacidade") {
                            Text(state.pack?.coverage ?: "Carregando banco local…")
                            Text("Sem anúncios, telemetria ou senhas. Dados ficam neste aparelho. Consultas UID são enviadas ao Enka; imagens e fontes usam seus respectivos provedores.")
                            Button(onClick={ export.launch("gacha-hub-backup.json") },enabled=!busy) { Text("Exportar backup") }
                            OutlinedButton(onClick={openFile("backup")},enabled=!busy) { Text("Importar / mesclar backup") }
                            Text("Backup substitui contas com o mesmo ID. Exporte antes de importar um backup antigo.",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={openFile("content")},enabled=!busy) { Text("Importar pacote de conteúdo") }
                            Button(onClick={vm.sync()},enabled=!busy) { Text("Buscar atualizações de conteúdo") }
                            Text("Consulta o pacote publicado pelo Gacha Hub no GitHub. A atualização não altera seus personagens nem seus projetos salvos.",style=MaterialTheme.typography.bodySmall)
                            var customFeed by rememberSaveable { mutableStateOf(false) }
                            TextButton(onClick={customFeed=!customFeed}) { Text("Fonte de conteúdo personalizada") }
                            if(customFeed) {
                                var url by rememberSaveable { mutableStateOf("") }
                                Field("URL HTTPS do pacote",url,{url=it})
                                Button(onClick={vm.sync(url)},enabled=!busy && url.startsWith("https://")) { Text("Sincronizar fonte personalizada") }
                            }
                            Text("Pacote ${state.pack?.version ?: 0} • ${state.pack?.publishedAt ?: "—"}",style=MaterialTheme.typography.bodySmall)
                        } }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        pages.forEach { name -> FilterChip(selected=page==name,onClick={page=name},label={Text(name)}) }
                    }
                    val accounts = state.accounts.filter { it.game == game }
                    if(accounts.size>1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
                        accounts.forEach { a -> TextButton(onClick={accountId=a.id}) { Text(if(account?.id==a.id) "● ${a.name}" else a.name) } }
                    }
                    when(page) {
                        "Conta" -> AccountPage(game,account,vm,{openFile("import")})
                        "Resumo" -> Dashboard(game,account,state,{page=it})
                        "Personagens","Favoritos" -> CharactersPage(game,account,state,vm,page=="Favoritos")
                        "Builds" -> BuildsPage(game,account,state,vm)
                        "Planejamento" -> PlannerPage(game,account,state,vm)
                        "Times" -> TeamsPage(game,account,state,vm)
                        "Materiais" -> MaterialsPage(game,account,state,vm)
                        "Calendário" -> CalendarPage(game,account,state)
                    }
                }
            }
        }
        if(message != null) AlertDialog(onDismissRequest=vm::dismiss,title={Text("Gacha Hub")},text={Text(message!!)},confirmButton={TextButton(onClick=vm::dismiss){Text("Entendi")}})
    }
}
@Composable internal fun Section(title: String, content: @Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize(),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            content()
        }
    }
}
@Composable internal fun Field(label: String,value: String,onChange:(String)->Unit,number:Boolean=false) {
    OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable internal fun SourceView(source: Source) {
    val uri = LocalUriHandler.current
    Text("${source.name} • versão ${source.patch}\nAtualização: ${source.updatedAt ?: "não informada"} • consultado ${source.checkedAt}",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={runCatching { uri.openUri(source.url) }}) { Text("Abrir fonte ↗") }
}
@Composable internal fun EmptyAccount() { Text("Crie ou selecione uma conta na seção Conta.",Modifier.padding(20.dp)) }

@Composable private fun AccountPage(game: Game, account: Account?, vm: HubViewModel, importFile:()->Unit) {
    var name by rememberSaveable(game) { mutableStateOf("") }
    var uid by rememberSaveable(account?.id) { mutableStateOf(account?.uid ?: "") }
    var offset by rememberSaveable(account?.id) { mutableStateOf((account?.serverOffset ?: -5).toString()) }
    var reset by rememberSaveable(account?.id) { mutableStateOf((account?.resetHour ?: 4).toString()) }
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Section("Nova conta") {
            Field("Nome da conta",name,{name=it})
            Button(onClick={vm.perform("Conta criada") { vm.repository.saveAccount(Account(newId(),game,name.trim())) }},enabled=name.isNotBlank()) { Text("Criar conta") }
        } }
        if(account != null) item { Section("${account.name} • importação") {
            Field("UID público",uid,{uid=it},true)
            if(game != Game.WUWA) {
                Text("Importa apenas personagens exibidos na vitrine pública. Não usa HoYoLAB autenticado nem acessa inventário de materiais.")
                Button(onClick={vm.importUid(account,uid)}) { Text("Importar vitrine pelo UID") }
                if(game != Game.GENSHIN) Text("Atributos finais de HSR/ZZZ ainda exigem cadastro ou JSON normalizado. Equipamentos brutos são preservados.",style=MaterialTheme.typography.bodySmall)
            } else Text("Não foi verificado um serviço público de UID seguro e documentado para WuWa. Use JSON ou seleção manual.")
            OutlinedButton(onClick=importFile) { Text("Importar personagens por JSON") }
            Text("O arquivo deve usar o formato Gacha Hub descrito no README.",style=MaterialTheme.typography.bodySmall)
            Field("Servidor: diferença UTC (Américas: -5)",offset,{offset=it},true)
            Field("Hora de reset no servidor",reset,{reset=it},true)
            Text("Configure o servidor real; o calendário não usa o fuso do aparelho.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={vm.perform("Conta atualizada") {
                val current=vm.repository.snapshot().accounts.first { it.id==account.id }
                vm.repository.saveAccount(current.copy(uid=uid,serverOffset=offset.toInt(),resetHour=reset.toInt()))
            }}) { Text("Salvar UID e servidor") }
        } }
    }
}
@Composable private fun Dashboard(game: Game, account: Account?, state: HubState, go:(String)->Unit) {
    if(account==null) { Column(Modifier.padding(20.dp)) { EmptyAccount(); Button(onClick={go("Conta")}){Text("Cadastrar conta")}}; return }
    val projects=state.projects.filter { it.accountId==account.id && !it.completed }
    val today=ResourceMath.serverDay(Instant.now().epochSecond,account.serverOffset,account.resetHour)
    val needed=projects.flatMap { it.costs.keys }.toSet()
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Section("Seu progresso") {
            Text("${account.characters.size} personagens possuídos",style=MaterialTheme.typography.headlineMedium)
            Text("${state.characters.count { it.game==game }} personagens no catálogo local (parcial)")
            Text("${projects.size} projetos ativos • ${state.teams.count { it.accountId==account.id }} times")
            Text("${account.characters.count { it.stats.isEmpty() }} personagens com atributos pendentes")
            val favorite=account.characters.firstOrNull { it.favorite }?.characterId
            Text("Favorito: ${state.characters.find { it.id==favorite }?.name ?: "Ainda não selecionado"}")
        } }
        item { Section("Farm de hoje") {
            val mats=state.materials.filter { it.game==game && it.id in needed && (it.days.isEmpty() || today in it.days) }
            if(mats.isEmpty()) Text("Nenhum material de projeto com disponibilidade registrada para hoje.")
            mats.forEach { Text("• ${it.name} — ${it.location.ifBlank { "Consulte a fonte" }}") }
            Button(onClick={go("Planejamento")}){Text("Abrir projetos")}
        } }
        item { Section("Cobertura dos dados") { Text(state.pack?.coverage ?: "Sem conteúdo"); Text("Vitrine pública não representa toda a conta. Complete sua coleção manualmente.") } }
    }
}

@Composable private fun CharactersPage(game: Game, account: Account?, state: HubState, vm: HubViewModel, favorites:Boolean) {
    var query by rememberSaveable(game) { mutableStateOf("") }
    var ownedOnly by rememberSaveable(game) { mutableStateOf(false) }
    var rarity by rememberSaveable(game) { mutableStateOf(0) }
    var editor by remember { mutableStateOf<Owned?>(null) }
    var create by remember { mutableStateOf(false) }
    val owned = account?.characters.orEmpty().associateBy { it.characterId }
    val chars=state.characters.filter { it.game==game && ResourceMath.matches(it.name,it.element,it.role,it.specialty,query) && (!ownedOnly || it.id in owned) && (!favorites || owned[it.id]?.favorite==true) && (rarity==0 || it.rarity==rarity) }.sortedBy { it.name }
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Field("Buscar nome, elemento, função ou especialidade",query,{query=it})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(ownedOnly,{ownedOnly=!ownedOnly},label={Text("Possuídos")})
                FilterChip(rarity==5,{rarity=if(rarity==5)0 else 5},label={Text("5★ / S")})
                FilterChip(rarity==4,{rarity=if(rarity==4)0 else 4},label={Text("4★ / A")})
            }
            OutlinedButton(onClick={create=true}){Text("Cadastrar personagem ausente")}
        }
        if(chars.isEmpty()) item { Text("Nenhum personagem encontrado. Adicione ao catálogo ou importe um pacote.") }
        items(chars,key={it.id}) { c -> Section(c.name) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(c.image.isNotBlank()) AsyncImage(model=c.image,contentDescription=c.name,modifier=Modifier.size(76.dp))
                Column { Text("${c.rarity}★ • ${c.element} • ${c.specialty}"); Text(c.role)
                    owned[c.id]?.let { Text("Lv. ${it.level} • ${game.copyTerm} ${it.copies}") }
                }
            }
            if(account != null) Row {
                if(c.id !in owned) Button(onClick={vm.perform { vm.repository.editOwned(account.id,Owned(c.id)) }}){Text("Tenho este")}
                else {
                    TextButton(onClick={editor=owned[c.id]}){Text("Editar progresso")}
                    TextButton(onClick={vm.perform { val o=vm.repository.snapshot().accounts.first{it.id==account.id}.characters.first{it.characterId==c.id}; vm.repository.editOwned(account.id,o.copy(favorite=!o.favorite)) }}){Text(if(owned[c.id]?.favorite==true) "★ Favorito" else "☆ Favoritar")}
                }
            }
        } }
    }
    if(create) {
        var name by remember { mutableStateOf("") }; var element by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }; var specialty by remember { mutableStateOf("") }; var stars by remember { mutableStateOf("5") }
        var image by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={create=false},title={Text("Novo personagem • ${game.name}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Field("Nome",name,{name=it}); Field("Elemento / atributo",element,{element=it});Field("Função",role,{role=it});Field("Caminho / especialidade",specialty,{specialty=it}); Field("Raridade 4 ou 5",stars,{stars=it},true);Field("Imagem HTTPS (opcional)",image,{image=it})
        }},confirmButton={TextButton(onClick={vm.perform("Personagem cadastrado") { vm.repository.saveCharacter(Character("${game.name.lowercase()}:manual-${newId()}",game,name.trim(),stars.toInt(),element,specialty,role,image));create=false }}){Text("Salvar")}},dismissButton={TextButton(onClick={create=false}){Text("Cancelar")}})
    }
    editor?.let { o -> OwnedEditor(game,state.characters.first { it.id==o.characterId },o,{editor=null}) { edited ->
        vm.perform("Progresso salvo") { vm.repository.editOwned(requireNotNull(account).id,edited); editor=null }
    } }
}
@Composable private fun OwnedEditor(game: Game, character: Character, initial: Owned, close:()->Unit, save:(Owned)->Unit) {
    var level by remember { mutableStateOf(initial.level.toString()) }; var copies by remember { mutableStateOf(initial.copies.toString()) }
    var asc by remember { mutableStateOf(initial.ascension.toString()) }; var notes by remember { mutableStateOf(initial.notes) }
    var stats by remember { mutableStateOf(codec.encodeToString(initial.stats)) }; var skills by remember { mutableStateOf(codec.encodeToString(initial.skills)) }
    var weapon by remember { mutableStateOf(codec.encodeToString(initial.weapon ?: Gear())) }; var equipment by remember { mutableStateOf(codec.encodeToString(initial.equipment)) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidStats = remember { mutableStateMapOf<String,Boolean>() }
    AlertDialog(onDismissRequest=close,title={Text(character.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Field("Nível (1–${game.maxLevel})",level,{level=it},true);Field(game.copyTerm,copies,{copies=it},true);Field("Ascensão (0–6)",asc,{asc=it},true)
        Text("Atributos em unidades da tela: porcentagens como 70, não 0.70. Use nomes do comparador: ATK, CRIT Rate, CRIT DMG, SPD…")
        val statKeys = listOf("ATK","HP","DEF","CRIT Rate","CRIT DMG") + when(game) {
            Game.GENSHIN -> listOf("Energy Recharge","Elemental Mastery")
            Game.HSR -> listOf("SPD","Energy Regen","Break Effect")
            Game.ZZZ -> listOf("Anomaly Proficiency","Anomaly Mastery","Impact")
            Game.WUWA -> listOf("Energy Regen")
        }
        statKeys.forEach { key ->
            var value by remember { mutableStateOf(initial.stats[key]?.toString().orEmpty()) }
            Field(key,value,{ text ->
                value=text
                val map=runCatching{codec.decodeFromString<Map<String,Double>>(stats)}.getOrDefault(emptyMap()).toMutableMap()
                if(text.isBlank()) map.remove(key) else text.replace(',','.').toDoubleOrNull()?.let { map[key]=it }
                stats=codec.encodeToString(map)
                invalidStats[key]=text.isNotBlank() && text.replace(',','.').toDoubleOrNull()==null
                error=if(text.isNotBlank() && text.replace(',','.').toDoubleOrNull()==null) "Valor inválido para $key" else null
            },true)
        }
        Text("Editor avançado (JSON):",style=MaterialTheme.typography.labelLarge)
        OutlinedTextField(stats,{stats=it},label={Text("Atributos JSON")},minLines=3)
        OutlinedTextField(skills,{skills=it},label={Text("Habilidades JSON — níveis base")},minLines=2)
        OutlinedTextField(weapon,{weapon=it},label={Text("${game.weaponTerm} — JSON")},minLines=3)
        OutlinedTextField(equipment,{equipment=it},label={Text("${game.gearTerm} — JSON")},minLines=3)
        Field("Observações",notes,{notes=it});error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={try {
        require(invalidStats.values.none { it }) { "Corrija os atributos inválidos" }
        val o=initial.copy(level=level.toInt(),copies=copies.toInt(),ascension=asc.toInt(),notes=notes,
            stats=codec.decodeFromString(stats),skills=codec.decodeFromString(skills),weapon=codec.decodeFromString<Gear>(weapon).takeIf { it.id.isNotBlank() || it.name.isNotBlank() },equipment=codec.decodeFromString(equipment))
        o.validate(game);save(o)
    }catch(e:Exception){error=e.message}}){Text("Salvar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
@Composable private fun BuildsPage(game:Game,account:Account?,state:HubState,vm:HubViewModel) {
    var query by rememberSaveable(game) { mutableStateOf("") }
    val chars=state.characters.filter{it.game==game}.associateBy{it.id}
    val builds=state.pack?.builds.orEmpty().filter { it.characterId in chars && chars[it.characterId]!!.name.contains(query,true) }
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Field("Buscar build por personagem",query,{query=it}) }
        if(builds.isEmpty())item{Text("Não há build verificada neste pacote para a busca. Nenhuma recomendação será gerada sem fonte.")}
        items(builds,key={it.id}) { b -> Section("${chars[b.characterId]?.name} • ${b.title}") {
            Text(b.role,color=MaterialTheme.colorScheme.primary)
            if(b.bis.isNotEmpty())Text("Best in Slot: ${b.bis.joinToString()}")
            if(b.premium.isNotEmpty())Text("Premium: ${b.premium.joinToString()}")
            if(b.accessible.isNotEmpty())Text("Acessíveis / F2P: ${b.accessible.joinToString()}")
            Text("Conjuntos: ${b.sets.joinToString("; ")}")
            b.slots.forEach{(slot,stat)->Text("$slot → $stat")}
            Text("Substats: ${b.substats.joinToString(" > ")}")
            Text("Habilidades: ${b.skillPriority.joinToString(" > ")}")
            if(b.rotation.isNotBlank())Text("Rotação: ${b.rotation}")
            if(b.notes.isNotBlank())Text(b.notes)
            val owned=account?.characters?.find{it.characterId==b.characterId}
            if(owned!=null && account!=null) {
                Text("Comparador • ${owned.level}/${game.maxLevel}",fontWeight=FontWeight.Bold)
                Text("Classificações são faixas editoriais do app, não simulação de DPS. Confira o contexto da referência.",style=MaterialTheme.typography.bodySmall)
                b.benchmarks.forEach { bench ->
                    val current=owned.stats[bench.stat]
                    val grade=current?.let { ResourceMath.compare(it,bench.low,bench.adequate,bench.excellent) } ?: "Sem atributo cadastrado"
                    Text("${bench.stat}: ${current ?: "—"}${bench.unit} → ${bench.adequate}–${bench.excellent}${bench.unit}\n$grade")
                    if(current!=null && current<bench.adequate)Text("Faltam ${String.format(java.util.Locale.ROOT,"%.1f",bench.adequate-current)}${if(bench.unit=="%") " pontos percentuais" else bench.unit}")
                    if(current!=null && bench.cap!=null && current>bench.cap)Text("Acima do limite útil ${bench.cap}${bench.unit}")
                    Text(bench.context,style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick={vm.perform("Build selecionada") { vm.repository.editOwned(account.id,owned.copy(buildId=b.id)) }}){Text(if(owned.buildId==b.id)"Build selecionada ✓" else "Usar esta build")}
            } else Text("Marque o personagem como possuído para comparar atributos.")
            b.sources.forEach { SourceView(it) }
        } }
    }
}
@Composable private fun MaterialsPage(game:Game,account:Account?,state:HubState,vm:HubViewModel) {
    if(account==null){EmptyAccount();return}
    var query by rememberSaveable(game){mutableStateOf("")}
    var adding by remember { mutableStateOf(false) }
    val needed=ResourceMath.sum(state.projects.filter{it.accountId==account.id && !it.completed}.map{it.costs})
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Field("Buscar material",query,{query=it});OutlinedButton(onClick={adding=true}){Text("Cadastrar material manual")}}
        items(state.materials.filter{it.game==game && it.name.contains(query,true)},key={it.id}){m->Section(m.name){
            var quantity by rememberSaveable(account.id,m.id,account.inventory[m.id]){mutableStateOf((account.inventory[m.id] ?: 0).toString())}
            Text("${m.category} • ${m.location}")
            Text("Necessário em projetos: ${needed[m.id] ?: 0} • faltam ${ResourceMath.missing(needed[m.id] ?: 0,account.inventory[m.id] ?: 0)}")
            Field("Quantidade no inventário",quantity,{quantity=it},true)
            Button(onClick={vm.perform("Inventário atualizado"){vm.repository.inventory(account.id,m.id,quantity.toLong())}}){Text("Salvar quantidade")}
            if(m.days.isNotEmpty())Text("Dias: ${m.days.joinToString{dayName(it)}}")
        }}
    }
    if(adding) {
        var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }
        var days by remember { mutableStateOf(setOf<Int>()) }
        AlertDialog(onDismissRequest={adding=false},title={Text("Material • ${game.name}")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Field("Nome do material",name,{name=it});Field("Onde farmar",location,{location=it})
            Text("Marque dias restritos; deixe vazio se não há bloqueio diário.")
            (1..7).forEach { d -> FilterChip(d in days,{days=if(d in days)days-d else days+d},label={Text(dayName(d))}) }
        }},confirmButton={TextButton(onClick={vm.perform("Material cadastrado") {
            vm.repository.saveMaterial(Material("${game.name.lowercase()}:manual-${newId()}",game,name,"Manual",days.sorted(),location));adding=false
        }},enabled=name.isNotBlank()){Text("Salvar")}},dismissButton={TextButton(onClick={adding=false}){Text("Cancelar")}})
    }
}
private fun dayName(day:Int)=listOf("Seg","Ter","Qua","Qui","Sex","Sáb","Dom")[day-1]
@Composable private fun CalendarPage(game:Game,account:Account?,state:HubState) {
    if(account==null){EmptyAccount();return}
    val day=ResourceMath.serverDay(Instant.now().epochSecond,account.serverOffset,account.resetHour)
    val needed=ResourceMath.sum(state.projects.filter{it.accountId==account.id && !it.completed}.map{it.costs})
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Reset às ${account.resetHour}h • UTC${account.serverOffset} • hoje no servidor: ${dayName(day)}")}
        items(listOf(0,1)){offset->Section(if(offset==0)"Hoje" else "Amanhã"){
            val target=(day-1+offset)%7+1
            val mats=state.materials.filter{it.game==game && (it.days.isEmpty() || target in it.days) && (needed[it.id] ?: 0)>(account.inventory[it.id] ?: 0)}
            if(mats.isEmpty())Text("Nenhum material faltante com disponibilidade registrada.")
            mats.forEach{Text("• ${it.name} — ${it.location.ifBlank{"Consulte no jogo"}}")}
        }}
        item{Text("Materiais sem bloqueio de dia são mostrados diariamente; isso não garante respawn nem tentativas semanais. O pacote inicial não possui calendário completo de domínios.",Modifier.padding(4.dp))}
        val now=Instant.now()
        items(state.pack?.banners.orEmpty().filter{it.game==game && Instant.parse(it.endsAt)>now}){b->Section(b.name){Text("${b.startsAt} → ${b.endsAt}");SourceView(b.source)}}
        if(state.pack?.banners.orEmpty().none{it.game==game})item{Text("Sem banners verificados neste pacote.")}
    }
}

private fun parseManualAmounts(values: Map<String,String>): Map<String,Long> = values.filterValues { it.isNotBlank() }.mapValues { (_,v) ->
    v.toLong().also { require(it in 0..1_000_000_000) { "Quantidade inválida" } }
}.filterValues { it > 0 }
