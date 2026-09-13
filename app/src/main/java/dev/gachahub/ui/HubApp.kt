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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    GachaTheme(game) {
        BackHandler(game != null || globalPlanning) { gameName = null; globalPlanning = false; page = "Resumo" }
        Scaffold(containerColor=GachaTokens.canvas, topBar={ TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent), title={ Column {
            Text(game?.title ?: if(globalPlanning) "Planejamentos" else "Gacha Hub", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
            Text(account?.name?.takeIf { game != null } ?: "Sua coleção. Seu próximo objetivo.",style=MaterialTheme.typography.labelMedium,color=GachaTokens.muted)
        } },navigationIcon={ if(game != null || globalPlanning) TextButton(onClick={gameName=null;globalPlanning=false}) { Text("‹", style=MaterialTheme.typography.headlineSmall) } }) },
            bottomBar={ if(game != null) GachaNavigationBar(page) { page=it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(busy) LoadingState(modifier=Modifier.padding(horizontal=18.dp, vertical=6.dp))
                if(globalPlanning) {
                    GlobalPlanningPage(state) { project ->
                        val target = state.accounts.first { it.id == project.accountId }
                        globalPlanning=false; gameName=target.game.name; accountId=target.id; page="Planejamento"
                    }
                } else if(game == null) {
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        item { HomeHero(state, accent) { globalPlanning=true } }
                        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.fillMaxWidth()) {
                            MetricPill("Projetos ativos", state.projects.count { !it.completed }.toString(), accent, modifier=Modifier.weight(1f))
                            MetricPill("Contas", state.accounts.size.toString(), accent, modifier=Modifier.weight(1f))
                        } }
                        items(Game.entries) { g ->
                            GameCard(g, state, onClick={ gameName=g.name; accountId=state.accounts.firstOrNull { it.game==g }?.id; page="Resumo" })
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
@Composable private fun HomeHero(state: HubState, accent: Color, openPlanner: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(
            Brush.linearGradient(listOf(accent.copy(alpha=.28f), GachaTokens.panel, GachaTokens.panel))
        ).border(1.dp, accent.copy(alpha=.35f), RoundedCornerShape(30.dp)).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text("QUATRO MUNDOS, UM HUB", style=MaterialTheme.typography.labelLarge, color=accent, fontWeight=FontWeight.Bold)
        Text("Seu espaço de progresso", style=MaterialTheme.typography.displaySmall)
        Text("Sua coleção, suas builds e o próximo objetivo em um só lugar.", color=GachaTokens.muted)
        Button(onClick=openPlanner, modifier=Modifier.fillMaxWidth()) { Text("Todos os planejamentos (${state.projects.count { !it.completed }})") }
    }
}

@Composable private fun GameCard(game: Game, state: HubState, onClick: () -> Unit) {
    val accent = Color(game.accent)
    Card(onClick=onClick, modifier=Modifier.fillMaxWidth().animateContentSize(), shape=RoundedCornerShape(26.dp), colors=CardDefaults.cardColors(containerColor=GachaTokens.panelRaised), border=androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha=.32f))) {
        Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha=.42f), GachaTokens.panel)))) {
                Text(game.title.take(2).uppercase(), Modifier.align(Alignment.Center), color=accent, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(game.title, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                Text("${state.accounts.count { it.game==game }} contas • ${state.characters.count { it.game==game }} personagens no catálogo", color=GachaTokens.muted, style=MaterialTheme.typography.bodySmall)
                Text("Personagens • Builds • Planner", color=accent, style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.SemiBold)
            }
            Text("›", color=accent, style=MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable private fun ActionTile(label: String, icon: String, onClick: () -> Unit) {
    FilledTonalButton(onClick=onClick, modifier=Modifier.heightIn(min=52.dp), contentPadding=PaddingValues(horizontal=12.dp, vertical=10.dp)) {
        Text("$icon  $label", maxLines=1)
    }
}

@Composable internal fun Section(title: String, content: @Composable ColumnScope.()->Unit) {
    GachaCard(Modifier.fillMaxWidth().animateContentSize()) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        content()
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
@Composable internal fun EmptyAccount() { EmptyState("Nenhuma conta selecionada", "Crie ou selecione uma conta na seção Conta.", modifier=Modifier.padding(20.dp)) }

@Composable private fun AccountPage(game: Game, account: Account?, vm: HubViewModel, importFile:()->Unit) {
    var name by rememberSaveable(game) { mutableStateOf("") }
    var uid by rememberSaveable(account?.id) { mutableStateOf(account?.uid ?: "") }
    var offset by rememberSaveable(account?.id) { mutableStateOf((account?.serverOffset ?: -5).toString()) }
    var reset by rememberSaveable(account?.id) { mutableStateOf((account?.resetHour ?: 4).toString()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Section("Nova conta") {
            Field("Nome da conta",name,{name=it})
            Button(onClick={vm.perform("Conta criada") { vm.repository.saveAccount(Account(newId(),game,name.trim())) }},enabled=name.isNotBlank()) { Text("Criar conta") }
        }
        if(account != null) Section("${account.name} • importação") {
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
        }
        else Section("Importar perfil") {
            Field("UID público",uid,{uid=it},true)
            Text("Crie uma conta acima para habilitar a importação e o vínculo do UID.", style=MaterialTheme.typography.bodySmall, color=GachaTokens.muted)
        }
    }
}
@Composable private fun Dashboard(game: Game, account: Account?, state: HubState, go:(String)->Unit) {
    if(account==null) { Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) { EmptyState("Nenhuma conta conectada", "Crie uma conta ou importe um perfil para acompanhar seu progresso.", action={ Button(onClick={go("Conta")}){Text("Cadastrar conta")} }) }; return }
    val projects=state.projects.filter { it.accountId==account.id && !it.completed }
    val today=ResourceMath.serverDay(Instant.now().epochSecond,account.serverOffset,account.resetHour)
    val needed=projects.flatMap { it.costs.keys }.toSet()
    val favoriteCharacters=account.characters.filter { it.favorite }.mapNotNull { owned -> state.characters.find { it.id==owned.characterId } }
    val activeBuilds=account.characters.count { it.buildId.isNotBlank() }
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { HeroPanel(game, account.name.ifBlank { "Minha conta" }, "Seu painel de progresso") {
            StatusBadge(if(account.uid.isBlank()) "UID pendente" else "Perfil conectado", positive=account.uid.isNotBlank())
        } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.fillMaxWidth()) {
            MetricPill("Possuídos", account.characters.size.toString(), modifier=Modifier.weight(1f))
            MetricPill("Projetos", projects.size.toString(), modifier=Modifier.weight(1f))
            MetricPill("Times", state.teams.count { it.accountId==account.id }.toString(), modifier=Modifier.weight(1f))
        } }
        item { Section("Ações rápidas") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActionTile("Personagens", "✦") { go("Personagens") }
                ActionTile("Planner", "▤") { go("Planejamento") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActionTile("Times", "◈") { go("Times") }
                ActionTile("Materiais", "◇") { go("Materiais") }
            }
        } }
        item { Section("Seu progresso") {
            Text("${account.characters.count { it.stats.isEmpty() }} personagens com atributos pendentes",color=GachaTokens.muted)
            Text("$activeBuilds builds vinculadas • ${state.characters.count { it.game==game }} no catálogo local",color=GachaTokens.muted)
            val favorite=account.characters.firstOrNull { it.favorite }?.characterId
            Text("Favorito: ${state.characters.find { it.id==favorite }?.name ?: "Ainda não selecionado"}")
        } }
        item { Section("Favoritos") {
            if(favoriteCharacters.isEmpty()) EmptyState("Nenhum favorito", "Marque personagens no catálogo para vê-los rapidamente aqui.")
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                favoriteCharacters.take(6).forEach { c ->
                    Column(Modifier.width(88.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        CharacterArtwork(c, true, Modifier.size(76.dp))
                        Text(c.name, style=MaterialTheme.typography.labelSmall, maxLines=1)
                    }
                }
            }
        } }
        item { Section("Farm de hoje") {
            val mats=state.materials.filter { it.game==game && it.id in needed && (it.days.isEmpty() || today in it.days) }
            if(mats.isEmpty()) EmptyState("Farm limpo", "Nenhum material de projeto com disponibilidade registrada para hoje.")
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
    var attribute by rememberSaveable(game) { mutableStateOf("") }
    var specialty by rememberSaveable(game) { mutableStateOf("") }
    var faction by rememberSaveable(game) { mutableStateOf("") }
    var grid by rememberSaveable(game) { mutableStateOf(true) }
    var detail by remember { mutableStateOf<Character?>(null) }
    var editor by remember { mutableStateOf<Owned?>(null) }
    var create by remember { mutableStateOf(false) }
    val owned = account?.characters.orEmpty().associateBy { it.characterId }
    val chars=state.characters.filter { it.game==game && ResourceMath.matches(it.name,it.element,it.role + " " + it.faction,it.specialty,query) && (attribute.isBlank() || it.element==attribute) && (specialty.isBlank() || it.specialty==specialty) && (faction.isBlank() || it.faction==faction) && (!ownedOnly || it.id in owned) && (!favorites || owned[it.id]?.favorite==true) && (rarity==0 || it.rarity==rarity) }.sortedBy { it.name }
    if(detail != null) {
        CharacterDetail(game, detail!!, owned[detail!!.id], state, onBack={detail=null}, onEdit={ownedDetail -> editor=ownedDetail})
    } else Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=18.dp, vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if(favorites) "Favoritos" else "Personagens", style=MaterialTheme.typography.headlineSmall)
                    Text("${chars.size} resultados", color=GachaTokens.muted, style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={grid=!grid}) { Text(if(grid) "☷ Lista" else "▦ Grade") }
            }
            Field("Buscar nome, atributo, função, especialidade ou facção",query,{query=it})
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AnimatedFilterChip(ownedOnly,{ownedOnly=!ownedOnly},"Possuídos")
                AnimatedFilterChip(rarity==5,{rarity=if(rarity==5)0 else 5},if(game==Game.ZZZ) "Rank S" else "5★")
                AnimatedFilterChip(rarity==4,{rarity=if(rarity==4)0 else 4},if(game==Game.ZZZ) "Rank A" else "4★")
            }
            if(game==Game.ZZZ) {
                val roster=state.characters.filter { it.game==game }
                CatalogFilter("Atributo",attribute,{attribute=it},roster.map{it.element})
                CatalogFilter("Especialidade",specialty,{specialty=it},roster.map{it.specialty})
                CatalogFilter("Facção",faction,{faction=it},roster.map{it.faction})
            }
            OutlinedButton(onClick={create=true}){Text("Cadastrar personagem ausente")}
        }
        if(chars.isEmpty()) {
            EmptyState("Nenhum personagem encontrado", "Ajuste a busca ou importe um pacote de conteúdo.", modifier=Modifier.padding(18.dp))
        } else if(grid) {
            LazyVerticalGrid(columns=GridCells.Adaptive(156.dp), modifier=Modifier.fillMaxSize(), contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(chars, key={it.id}) { c -> CharacterCard(game,c,owned[c.id],account,vm,{editor=it},{detail=c}) }
            }
        } else {
            LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(chars,key={it.id}) { c -> CharacterCard(game,c,owned[c.id],account,vm,{editor=it},{detail=c},compact=true) }
            }
        }
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

@Composable private fun CharacterCard(game: Game, character: Character, owned: Owned?, account: Account?, vm: HubViewModel, edit: (Owned) -> Unit, open: () -> Unit, compact: Boolean=false) {
    GachaCard(Modifier.fillMaxWidth(), onClick=open) {
        if(compact) Row(horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
            CharacterArtwork(character, owned != null, Modifier.size(72.dp))
            CharacterMeta(game, character, owned, Modifier.weight(1f))
        } else {
            CharacterArtwork(character, owned != null, Modifier.fillMaxWidth().height(136.dp))
            CharacterMeta(game, character, owned)
        }
        if(account != null) {
            if(owned == null) Button(onClick={vm.perform { vm.repository.editOwned(account.id,Owned(character.id)) }}, modifier=Modifier.fillMaxWidth()) { Text("Tenho este") }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                TextButton(onClick={edit(owned)}, modifier=Modifier.weight(1f)) { Text("Editar") }
                TextButton(onClick={vm.perform { val current=vm.repository.snapshot().accounts.first{it.id==account.id}.characters.first{it.characterId==character.id}; vm.repository.editOwned(account.id,current.copy(favorite=!current.favorite)) }}, modifier=Modifier.weight(1f)) { Text(if(owned.favorite) "★" else "☆") }
            }
        }
    }
}

@Composable private fun CharacterDetail(game: Game, character: Character, owned: Owned?, state: HubState, onBack: () -> Unit, onEdit: (Owned) -> Unit) {
    LazyColumn(contentPadding=PaddingValues(18.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { TextButton(onClick=onBack) { Text("‹  Voltar ao catálogo") } }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha=.28f),GachaTokens.panel))).padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                CharacterArtwork(character, owned != null, Modifier.fillMaxWidth().height(240.dp))
                Text(character.name, style=MaterialTheme.typography.headlineSmall)
                Text("${if(game==Game.ZZZ) (if(character.rarity==5) "Rank S" else "Rank A") else "${character.rarity}★"} • ${character.element} • ${character.specialty}", color=MaterialTheme.colorScheme.primary)
                Text("${character.role}${if(character.faction.isBlank()) "" else " • ${character.faction}"}", color=GachaTokens.muted)
            }
        }
        item { Section("Visão geral") {
            if(owned == null) Text("Este personagem ainda não está marcado na conta.", color=GachaTokens.muted)
            else {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MetricPill("Nível", "${owned.level}/${game.maxLevel}", modifier=Modifier.weight(1f))
                    MetricPill(if(game==Game.ZZZ) "Mindscape" else game.copyTerm, owned.copies.toString(), modifier=Modifier.weight(1f))
                }
                Text(if(owned.weapon?.name.isNullOrBlank()) "Equipamento principal não cadastrado" else "${game.weaponTerm}: ${owned.weapon?.name}", color=GachaTokens.muted)
                Button(onClick={onEdit(owned)}, modifier=Modifier.fillMaxWidth()) { Text("Editar progresso") }
            }
        } }
        item { Section("Build e progresso") {
            val build=state.pack?.builds?.find { it.id==owned?.buildId }
            Text(build?.title ?: "Nenhuma build vinculada", color=GachaTokens.muted)
            Text("Use o Planner para definir a próxima evolução e acompanhar os materiais.")
        } }
    }
}

@Composable private fun CharacterMeta(game: Game, character: Character, owned: Owned?, modifier: Modifier=Modifier) {
    Column(modifier, verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(character.name, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, maxLines=1)
        Text("${if(game==Game.ZZZ) (if(character.rarity==5) "S" else "A") else "${character.rarity}★"} • ${character.element}", color=MaterialTheme.colorScheme.primary, style=MaterialTheme.typography.labelMedium)
        Text("${character.specialty} • ${character.role}", color=GachaTokens.muted, style=MaterialTheme.typography.bodySmall, maxLines=2)
        if(character.faction.isNotBlank()) Text(character.faction, color=GachaTokens.muted, style=MaterialTheme.typography.bodySmall, maxLines=1)
        if(owned != null) Text("Lv. ${owned.level} • ${if(game==Game.ZZZ) "M${owned.copies}" else "${game.copyTerm} ${owned.copies}"}", color=GachaTokens.success, style=MaterialTheme.typography.labelMedium)
    }
}
@Composable private fun CatalogFilter(label:String, selected:String, change:(String)->Unit, values:List<String>) {
    Text(label,style=MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected.isBlank(),{change("")},label={Text("Todos")})
        values.filter{it.isNotBlank()}.distinct().sorted().forEach { value ->
            FilterChip(selected==value,{change(if(selected==value) "" else value)},label={Text(value)})
        }
    }
}
@Composable private fun OwnedEditor(game: Game, character: Character, initial: Owned, close:()->Unit, save:(Owned)->Unit) {
    var level by remember { mutableStateOf(initial.level.toString()) }; var copies by remember { mutableStateOf(initial.copies.toString()) }
    var asc by remember { mutableStateOf(initial.ascension.toString()) }; var notes by remember { mutableStateOf(initial.notes) }
    var stats by remember { mutableStateOf(codec.encodeToString(initial.stats)) }; var skills by remember { mutableStateOf(codec.encodeToString(if(game==Game.ZZZ) ZzzProgress.skills(initial.skills) else initial.skills)) }
    var weapon by remember { mutableStateOf(codec.encodeToString(initial.weapon ?: Gear())) }; var equipment by remember { mutableStateOf(codec.encodeToString(initial.equipment)) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidStats = remember { mutableStateMapOf<String,Boolean>() }
    AlertDialog(onDismissRequest=close,title={Text(character.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Field("Nível (1–${game.maxLevel})",level,{level=it},true);if(game==Game.ZZZ) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                (0..6).forEach { n -> FilterChip(copies==n.toString(),{copies=n.toString()},label={Text("M$n")}) }
            }
            Field("Promoção (0–5)",asc,{asc=it},true)
            Text("Níveis base, sem bônus de Mindscape. Core: 0 = sem melhoria, 1–6 = A–F.")
            (ZzzProgress.tracks+"Core").forEach { track ->
                val map=runCatching{codec.decodeFromString<Map<String,Int>>(skills)}.getOrDefault(emptyMap())
                var value by remember(track) { mutableStateOf(map[track]?.toString().orEmpty()) }
                Field(if(track=="Core") "Core (0–6 / A–F)" else "$track (1–12)",value,{ text ->
                    value=text
                    val next=map.toMutableMap()
                    if(text.isBlank()) next.remove(track) else text.toIntOrNull()?.let { next[track]=it }
                    invalidStats[track]=text.isNotBlank() && (text.toIntOrNull()?.let { it in (if(track=="Core") 0..6 else 1..12) } != true)
                    skills=codec.encodeToString(next)
                },true)
            }
        } else { Field(game.copyTerm,copies,{copies=it},true);Field("Ascensão (0–6)",asc,{asc=it},true) }
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
        if(builds.isEmpty())item{EmptyState("Nenhuma build verificada", "Não há recomendação para esta busca. Importe conteúdo com fonte para preencher o catálogo.")}
        items(builds,key={it.id}) { b -> Section("${chars[b.characterId]?.name} • ${b.title}") {
            Text(b.role,color=MaterialTheme.colorScheme.primary)
            if(b.bis.isNotEmpty())Text("${if(game==Game.ZZZ) "Recomendação principal" else "Best in Slot"}: ${b.bis.joinToString()}")
            if(b.premium.isNotEmpty())Text("Premium: ${b.premium.joinToString()}")
            if(b.accessible.isNotEmpty())Text("Acessíveis / F2P: ${b.accessible.joinToString()}")
            Text("Conjuntos: ${b.sets.joinToString("; ")}")
            b.wEngineIds.mapNotNull { id -> state.pack?.wEngines?.find{it.id==id} }.forEach { motor ->
                Text("${motor.name} • ${motor.rarity} • ${motor.specialty} • ${motor.mainStat}",style=MaterialTheme.typography.bodySmall)
            }
            b.driveDiscIds.mapNotNull { id -> state.pack?.driveDiscs?.find{it.id==id} }.forEach { disc ->
                Text("${disc.name} — 2p: ${disc.twoPiece} • 4p: ${disc.fourPiece}",style=MaterialTheme.typography.bodySmall)
            }
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
                    val ratio=if(current==null || bench.excellent<=0.0) 0f else (current/bench.excellent).toFloat().coerceIn(0f,1f)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("${bench.stat}: ${current ?: "—"}${bench.unit} → ${bench.adequate}–${bench.excellent}${bench.unit}", modifier=Modifier.weight(1f))
                        StatusBadge(grade, positive=grade.contains("Excelente",true) || grade.contains("Adequado",true))
                    }
                    ProgressBar(ratio,Modifier.fillMaxWidth())
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
        val filteredMaterials=state.materials.filter{it.game==game && it.name.contains(query,true)}
        if(filteredMaterials.isEmpty()) item { EmptyState("Nenhum material encontrado", "Cadastre um recurso manual ou ajuste a busca.") }
        items(filteredMaterials,key={it.id}){m->Section(m.name){
            var quantity by rememberSaveable(account.id,m.id,account.inventory[m.id]){mutableStateOf((account.inventory[m.id] ?: 0).toString())}
            val held=account.inventory[m.id] ?: 0
            val required=needed[m.id] ?: 0
            Text("${m.category} • ${m.location}",color=GachaTokens.muted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MetricPill("Tenho", held.toString(), modifier=Modifier.weight(1f))
                MetricPill("Preciso", required.toString(), modifier=Modifier.weight(1f))
                MetricPill("Faltam", ResourceMath.missing(required,held).toString(), modifier=Modifier.weight(1f))
            }
            if(required>0) ProgressBar((held.toFloat()/required).coerceIn(0f,1f),Modifier.fillMaxWidth(),if(held>=required) GachaTokens.success else MaterialTheme.colorScheme.primary)
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
