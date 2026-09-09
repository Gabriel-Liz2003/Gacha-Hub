package dev.gachahub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gachahub.core.ResourceMath
import dev.gachahub.data.*
import dev.gachahub.data.Target

@Composable internal fun GlobalPlanningPage(state: HubState, open: (Project)->Unit) {
    var sort by rememberSaveable { mutableStateOf("Prioridade") }
    var activeOnly by rememberSaveable { mutableStateOf(true) }
    val accounts=state.accounts.associateBy { it.id }
    val characters=state.characters.associateBy { it.id }
    val allocations=state.accounts.associate { it.id to Planner.allocations(state,it.id) }
    fun progress(p: Project)=if(p.completed) 1.0 else ResourceMath.progress(p.costs,allocations[p.accountId]?.get(p.id).orEmpty())
    val projects=state.projects.filter { !activeOnly || !it.completed }.sortedWith(when(sort) {
        "Jogo" -> compareBy<Project> { accounts[it.accountId]?.game?.ordinal }.thenBy { it.priority }.thenBy { it.id }
        "Progresso" -> compareBy<Project> { progress(it) }.thenBy { it.id }
        "Personagem" -> compareBy<Project> { characters[it.characterId]?.name?.lowercase() }.thenBy { it.id }
        else -> compareBy<Project> { it.priority }.thenBy { it.id }
    })
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            SortChips(sort,{sort=it},listOf("Prioridade","Jogo","Progresso","Personagem"))
            FilterChip(activeOnly,{activeOnly=!activeOnly},label={Text("Somente ativos")})
            Text("Cada conta usa seu próprio inventário. Toque em um projeto para gerenciá-lo.")
        }
        if(projects.isEmpty()) item { Text("Nenhum planejamento neste filtro.") }
        items(projects,key={it.id}) { p ->
            Card(onClick={open(p)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(p.title,style=MaterialTheme.typography.titleMedium)
                    Text("${accounts[p.accountId]?.game?.title} • ${accounts[p.accountId]?.name}")
                    Text("${characters[p.characterId]?.name} • prioridade ${p.priority} • ${(progress(p)*100).toInt()}%")
                    LinearProgressIndicator(progress={progress(p).toFloat()},modifier=Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable private fun SortChips(selected: String, change:(String)->Unit, options:List<String>) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(selected==it,{change(it)},label={Text(it)}) }
    }
}

@Composable internal fun PlannerPage(game:Game,account:Account?,state:HubState,vm:HubViewModel) {
    if(account==null){EmptyAccount();return}
    var create by remember(account.id){mutableStateOf(false)}
    var editing by remember(account.id){mutableStateOf<Project?>(null)}
    var renaming by remember(account.id){mutableStateOf<Project?>(null)}
    var deleting by remember(account.id){mutableStateOf<Project?>(null)}
    var sort by rememberSaveable(account.id){mutableStateOf("Prioridade")}
    val allocations=Planner.allocations(state,account.id)
    val chars=state.characters.associateBy { it.id }
    fun progress(p:Project)=if(p.completed)1.0 else ResourceMath.progress(p.costs,allocations[p.id].orEmpty())
    val ps=state.projects.filter{it.accountId==account.id}.sortedWith(when(sort) {
        "Progresso" -> compareBy<Project>{progress(it)}.thenBy{it.id}
        "Personagem" -> compareBy<Project>{chars[it.characterId]?.name?.lowercase()}.thenBy{it.id}
        else -> compareBy<Project>{it.priority}.thenBy{it.id}
    })
    val materials=state.materials.associateBy{it.id}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Button(onClick={create=true},enabled=account.characters.isNotEmpty()){Text("Novo planejamento")}
            SortChips(sort,{sort=it},listOf("Prioridade","Progresso","Personagem"))
            Text("Inventário reservado por prioridade (menor número primeiro). Concluir consome os materiais; atualize o progresso do personagem após evoluí-lo no jogo.",style=MaterialTheme.typography.bodySmall)
        }
        if(ps.isEmpty())item{Text("Nenhum projeto. Cadastre um personagem e crie seu objetivo.")}
        items(ps,key={it.id}) { p -> Section(p.title) {
            val a=allocations[p.id].orEmpty()
            Text("${chars[p.characterId]?.name} • ${(progress(p)*100).toInt()}% • prioridade ${p.priority}")
            Text(if(p.manual) "Checklist manual" else "Custos verificados • pacote ${p.dataVersion}")
            LinearProgressIndicator(progress={progress(p).toFloat()},modifier=Modifier.fillMaxWidth())
            p.targets.forEach{Text("${it.track}: ${it.from} → ${it.to}")}
            p.costs.forEach { (id,need) ->
                val held=if(p.completed)need else a[id] ?: 0
                Text("${if(held>=need)"✓" else "□"} ${materials[id]?.name ?: id}: $held / $need • faltam ${need-held}")
                val m=materials[id]
                if(m?.energyPerRun!=null && m.estimatedYield!=null)Text("Energia estimada: ${ResourceMath.estimatedEnergy(need-held,m.estimatedYield,m.energyPerRun)} (rendimento médio)",style=MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={renaming=p}){Text("Nome e prioridade")}
                if(!p.completed)TextButton(onClick={editing=p}){Text("Editar objetivos")}
                TextButton(onClick={deleting=p}){Text("Excluir projeto")}
            }
            if(!p.completed) {
                Button(onClick={vm.perform("Projeto concluído; materiais consumidos") { vm.repository.completeProject(p.id) }},enabled=progress(p)>=1){Text("Concluir e consumir materiais")}
                TextButton(onClick={vm.perform("Prioridade atualizada") { vm.repository.renameProject(account.id,p.id,p.title,(p.priority-1).coerceAtLeast(0)) }}){Text("Priorizar ↑")}
            } else Text("Concluído ✓")
        } }
    }
    if(create || editing!=null) key(account.id,editing?.id) {
        ProjectEditor(game,account,state,editing,{create=false;editing=null}) { p ->
            vm.perform("Planejamento salvo") { vm.repository.saveProject(p);create=false;editing=null }
        }
    }
    renaming?.let { p -> key(p.id) {
        var title by rememberSaveable{mutableStateOf(p.title)}
        var priority by rememberSaveable{mutableStateOf(p.priority.toString())}
        AlertDialog(onDismissRequest={renaming=null},title={Text("Editar projeto")},text={Column {
            Field("Nome do projeto",title,{title=it});Field("Prioridade",priority,{priority=it},true)
            Text("Os materiais calculados são preservados.")
        }},confirmButton={TextButton(onClick={vm.perform("Projeto atualizado") {
            vm.repository.renameProject(account.id,p.id,title,priority.toInt());renaming=null
        }},enabled=title.isNotBlank() && (priority.toIntOrNull() ?: -1) in 0..1000000){Text("Salvar alterações")}},dismissButton={TextButton(onClick={renaming=null}){Text("Cancelar")}})
    } }
    deleting?.let { p -> DeleteDialog("Excluir ${p.title}?","O inventário permanece igual. A reserva deste projeto será liberada; materiais já consumidos não serão devolvidos.",{deleting=null}) {
        vm.perform("Projeto excluído") { vm.repository.deleteProject(account.id,p.id);deleting=null }
    } }
}

@Composable private fun ProjectEditor(game:Game,account:Account,state:HubState,initial:Project?,close:()->Unit,save:(Project)->Unit) {
    val characters=state.characters.filter { c -> account.characters.any{it.characterId==c.id} }
    if(characters.isEmpty())return
    var selected by rememberSaveable { mutableStateOf(initial?.characterId ?: characters.first().id) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var priority by rememberSaveable{mutableStateOf((initial?.priority ?: 0).toString())}
    val edges=state.pack?.costs.orEmpty().filter{it.characterId==selected}.groupBy { it.track }
    var manual by rememberSaveable(selected){mutableStateOf(initial?.manual ?: edges.isEmpty())}
    val enabledTracks=remember(selected){mutableStateMapOf<String,Boolean>().apply{initial?.takeIf{it.characterId==selected}?.targets?.forEach{put(it.track,true)}}}
    val from=remember(selected){mutableStateMapOf<String,String>().apply{initial?.takeIf{it.characterId==selected}?.targets?.forEach{put(it.track,it.from.toString())}}}
    val to=remember(selected){mutableStateMapOf<String,String>().apply{initial?.takeIf{it.characterId==selected}?.targets?.forEach{put(it.track,it.to.toString())}}}
    val amounts=remember{mutableStateMapOf<String,String>().apply{initial?.takeIf{it.manual}?.costs?.forEach{(id,n)->put(id,n.toString())}}}
    var formError by remember{mutableStateOf<String?>(null)}
    var preview by remember{mutableStateOf<Map<String,Long>?>(null)}
    fun targets()=enabledTracks.filterValues{it}.keys.map{track->Target(track,from[track]?.toIntOrNull() ?: error("Informe o valor atual de $track"),to[track]?.toIntOrNull() ?: error("Informe o objetivo de $track"))}
    fun calculate():Map<String,Long> {
        val costs=if(manual) amounts.filterValues{it.isNotBlank()}.mapValues{(_,value)->value.toLong().also{require(it in 0..1_000_000_000){"Quantidade inválida"}}}.filterValues{it>0}
            else Planner.calculate(requireNotNull(state.pack),selected,targets())
        require(costs.isNotEmpty()){"Selecione uma evolução ou informe materiais necessários"}
        return costs
    }
    AlertDialog(onDismissRequest=close,title={Text(if(initial==null) "Planejar personagem" else "Editar objetivos")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Personagem")
        Row(Modifier.horizontalScroll(rememberScrollState())) { characters.forEach { c -> FilterChip(selected==c.id,{selected=c.id;preview=null},label={Text(c.name)}) } }
        val owned=account.characters.first{it.characterId==selected}
        Text("Atual: nível ${owned.level} • ascensão ${owned.ascension} • ${game.weaponTerm} ${owned.weapon?.level ?: "não cadastrado"}")
        Field("Nome do projeto",title,{title=it});Field("Prioridade",priority,{priority=it},true)
        Row { Switch(manual,{manual=it;preview=null});Text("Checklist manual") }
        if(manual) {
            Text("Informe custos que você conferiu. Cadastre materiais ausentes na seção Materiais.")
            state.materials.filter{it.game==game}.forEach{m->Field(m.name,amounts[m.id].orEmpty(),{amounts[m.id]=it;preview=null},true)}
        } else {
            if(edges.isEmpty())Text("Sem tabela verificada para este personagem. Use checklist manual ou importe um pacote de conteúdo.")
            edges.forEach{(track,steps)->
                FilterChip(enabledTracks[track]==true,{
                    enabledTracks[track]=enabledTracks[track]!=true
                    if(track !in from) from[track]=steps.minOf{it.from}.toString()
                    if(track !in to) to[track]=steps.maxOf{it.to}.toString()
                    preview=null
                },label={Text(track)})
                if(enabledTracks[track]==true) {
                    Field("Atual • $track",from[track].orEmpty(),{from[track]=it;preview=null},true)
                    Field("Objetivo • $track",to[track].orEmpty(),{to[track]=it;preview=null},true)
                    Text("Etapas disponíveis: ${steps.sortedBy{it.from}.joinToString { "${it.from}→${it.to}" }}",style=MaterialTheme.typography.bodySmall)
                    steps.map{it.source}.distinct().forEach { SourceView(it) }
                }
            }
            Text("Somente as trilhas selecionadas entram no cálculo. Uma tabela total não permite estimar níveis intermediários; EXP, habilidades e arma precisam de suas próprias tabelas.",style=MaterialTheme.typography.bodySmall)
        }
        if(initial!=null)Text("Salvar recalcula os custos com o pacote atual e atualiza a reserva; não consome inventário.")
        TextButton(onClick={try{preview=calculate();formError=null}catch(e:Exception){formError=e.message}}){Text("Calcular prévia")}
        preview?.forEach{(id,n)->Text("${state.materials.find{it.id==id}?.name ?: id}: $n")}
        formError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={try {
        val costs=calculate()
        val rank=priority.toInt();require(rank in 0..1000000){"Prioridade inválida"}
        save(Project(initial?.id ?: newId(),account.id,selected,title.ifBlank{"Evoluir ${characters.first{it.id==selected}.name}"},rank,if(manual)emptyList() else targets(),costs,manual,state.pack?.version ?: 0))
    }catch(e:Exception){formError=e.message}}){Text(if(initial==null)"Salvar projeto" else "Salvar objetivos")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}

@Composable internal fun TeamsPage(game:Game,account:Account?,state:HubState,vm:HubViewModel) {
    if(account==null){EmptyAccount();return}
    val owned=account.characters.map{it.characterId}.toSet();val chars=state.characters.associateBy{it.id}
    var editing by rememberSaveable(account.id){mutableStateOf<String?>(null)}
    var name by rememberSaveable(account.id){mutableStateOf("")}
    var selected by rememberSaveable(account.id){mutableStateOf(listOf<String>())}
    var notes by rememberSaveable(account.id){mutableStateOf("")}
    var deleting by remember(account.id){mutableStateOf<Team?>(null)}
    fun reset(){editing=null;name="";selected=emptyList();notes=""}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Section(if(editing==null) "Montar time (${game.teamSize} vagas)" else "Editar time"){
            Field("Nome do time",name,{name=it})
            Text("A ordem de seleção define as vagas do time.")
            account.characters.forEach{c->FilterChip(c.characterId in selected,{selected=if(c.characterId in selected)selected-c.characterId else if(selected.size<game.teamSize)selected+c.characterId else selected},label={Text("${if(c.characterId in selected) "${selected.indexOf(c.characterId)+1}. " else ""}${chars[c.characterId]?.name ?: c.characterId} • ${chars[c.characterId]?.role.orEmpty()}")})}
            Field("Sinergias / rotação / observações",notes,{notes=it})
            Button(onClick={vm.perform("Time salvo"){vm.repository.saveTeam(Team(editing ?: newId(),account.id,name.trim(),selected,notes));reset()}},enabled=name.isNotBlank() && selected.isNotEmpty()){Text(if(editing==null)"Salvar time" else "Salvar alterações do time")}
            if(editing!=null)TextButton(onClick={reset()}){Text("Cancelar edição")}
        }}
        items(state.teams.filter{it.accountId==account.id},key={it.id}){t->Section(t.name){
            Text(t.members.joinToString(" • "){chars[it]?.name ?: it});Text(t.notes)
            Row {
                TextButton(onClick={editing=t.id;name=t.name;selected=t.members;notes=t.notes}){Text("Editar time")}
                TextButton(onClick={deleting=t}){Text("Excluir time")}
            }
            if(editing==t.id)Text("Edite os campos no início desta tela.")
        }}
        items(state.pack?.teams.orEmpty().filter{it.game==game},key={"guide:${it.id}"}){t->Section(t.name){
            Text("Referência teórica: ${t.slots.joinToString(" • "){slot->chars[slot.first()]?.name ?: slot.first()}}")
            val team=Planner.availableTeam(t,owned)
            Text(if(team==null)"Sua conta ainda não tem uma combinação completa desta recomendação." else "Disponível na sua conta: ${team.joinToString(" • "){chars[it]?.name ?: it}}")
            t.slots.forEachIndexed{i,slot->Text("Vaga ${i+1}: ${slot.joinToString(" / "){chars[it]?.name ?: it}}")}
            Text(t.explanation)
            Text("Seleção segue a ordem da fonte. Não é ranking global de DPS.",style=MaterialTheme.typography.bodySmall)
            if(team!=null)Button(onClick={vm.perform("Time salvo"){vm.repository.saveTeam(Team(newId(),account.id,t.name,team,t.explanation))}}){Text("Salvar combinação da conta")}
            SourceView(t.source)
        }}
    }
    deleting?.let{t->DeleteDialog("Excluir ${t.name}?","Os personagens continuam na sua conta.",{deleting=null}) {
        vm.perform("Time excluído"){vm.repository.deleteTeam(account.id,t.id);if(editing==t.id)reset();deleting=null}
    }}
}

@Composable private fun DeleteDialog(title:String,message:String,close:()->Unit,confirm:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(title)},text={Text(message)},confirmButton={TextButton(onClick=confirm){Text("Confirmar exclusão")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})
}
