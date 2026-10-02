package com.violinjourney.app.core.domain.events

/** Why a name of a kind of one's own cannot be saved (spec 3.36.9): there is none, or another kind has it. */
sealed interface KindNameProblem {
    data object Empty : KindNameProblem

    /** [name] — the other kind's name as the screen shows it: «Такой вид уже есть — „Урок“». */
    data class Taken(val name: String) : KindNameProblem
}

/** The kinds of events (spec 3.35, 3.36.9, 5.28): what there is, in what order, how each looks, what a new one gets. */
object KindRules {
    /**
     * The order of names for a caller that knows no language — a test: the codes of the lowercase letters. The screens
     * pass the alphabet of the interface (`Formats.alphabetical`), as the sections of the repertoire do.
     */
    val LOWERCASE_ORDER: Comparator<String> = compareBy { it.lowercase() }

    /**
     * Every kind there is: the four built-in ones in their order — with the colour given to one, else its own (spec 5.29
     * R9) — then those of one's own by creation. A colour out of the set (a row of a newer build) reads as the default
     * one: of the built-in kind, or of «Другое» for one's own.
     */
    fun all(stored: List<StoredKind>, config: EventsConfig): List<EventKind> {
        val recolored = stored.filterIsInstance<StoredKind.Recolor>().associate { it.kind to it.color }
        val builtIn = BuiltInKind.entries.map { kind ->
            builtIn(kind, recolored[kind]?.takeIf { it in 0 until config.colorCount } ?: config.defaultColorOf(kind))
        }
        val own = stored.filterIsInstance<StoredKind.Own>()
            .sortedWith(compareBy<StoredKind.Own> { it.createdAtEpochMs }.thenBy { it.id })
            .map { kind ->
                val color = kind.color.takeIf { it in 0 until config.colorCount } ?: config.defaultColorOf(BuiltInKind.OTHER)
                EventKind(KindRef.Custom(kind.id), KindLook(kind.sign, color), kind.name, kind.createdAtEpochMs)
            }
        return builtIn + own
    }

    /** As the form and the legend show them (spec 3.35): the built-in ones in their order, then one's own by [byName]. */
    fun ordered(kinds: List<EventKind>, byName: Comparator<String> = LOWERCASE_ORDER): List<EventKind> {
        val builtIn = kinds.filter { it.ref is KindRef.BuiltIn }.sortedBy { (it.ref as KindRef.BuiltIn).kind.ordinal }
        val own = kinds.filter { it.ref is KindRef.Custom }
            .sortedWith(compareBy<EventKind, String>(byName) { it.ownName.orEmpty() }.thenBy { (it.ref as KindRef.Custom).id })
        return builtIn + own
    }

    /**
     * What [ref] reads as: itself while the kind exists, «Другое» once a kind of one's own is gone (spec 3.35). A built-in
     * kind always exists, whether [kinds] has been read yet or not.
     */
    fun resolve(ref: KindRef, kinds: List<EventKind>): KindRef =
        if (ref is KindRef.BuiltIn || kinds.any { it.ref == ref }) ref else KindRef.OTHER

    /**
     * The kind [ref] stands for ([resolve]); [kinds] — as [all] gives them. A built-in kind missing from them — a list
     * not read yet — has its colour by default.
     */
    fun kindOf(ref: KindRef, kinds: List<EventKind>, config: EventsConfig): EventKind {
        val resolved = resolve(ref, kinds)
        return kinds.firstOrNull { it.ref == resolved }
            ?: (resolved as KindRef.BuiltIn).kind.let { builtIn(it, config.defaultColorOf(it)) }
    }

    fun lookOf(ref: KindRef, kinds: List<EventKind>, config: EventsConfig): KindLook = kindOf(ref, kinds, config).look

    private fun builtIn(kind: BuiltInKind, color: Int) =
        EventKind(KindRef.BuiltIn(kind), KindLook(KindSign.of(kind), color), ownName = null, createdAtEpochMs = 0)

    /**
     * Why [name] cannot be the name of a kind of one's own (spec 5.28, plan D34): nothing is left of it once the spaces at
     * its edges go, or another kind has it — whatever the case; [shownNames] are the names of all the kinds as the screen
     * shows them, the built-in ones in the language of the interface. [editedId] — the kind being edited: its own name is
     * not taken. Null — the name is fine.
     */
    fun nameProblem(name: String, editedId: Long?, shownNames: Map<KindRef, String>, config: EventsConfig): KindNameProblem? {
        val clean = EventRules.cleanKindName(name, config)
        if (clean.isEmpty()) return KindNameProblem.Empty
        val edited = editedId?.let { KindRef.Custom(it) }
        val taken = shownNames.entries.firstOrNull { (ref, shown) -> ref != edited && shown.trim().lowercase() == clean.lowercase() }
        return taken?.let { KindNameProblem.Taken(it.value) }
    }

    /**
     * The colour and the sign a new kind of one's own starts with (spec 3.36.9, plan D30): the first colour of the set no
     * kind has, by its number — all of them taken, the one fewest kinds have, the smaller number among equals; the same for
     * the twelve signs of one's own.
     */
    fun firstFree(kinds: List<EventKind>, config: EventsConfig): KindLook {
        val colors = kinds.groupingBy { it.look.color }.eachCount()
        val color = (0 until config.colorCount).minWith(compareBy<Int> { colors[it] ?: 0 }.thenBy { it })
        val signs = kinds.filter { it.ref is KindRef.Custom }.groupingBy { it.look.sign }.eachCount()
        val sign = KindSign.OWN.minWith(compareBy<KindSign> { signs[it] ?: 0 }.thenBy { it.ordinal })
        return KindLook(sign, color)
    }

    /**
     * The signs other kinds of one's own already have, with the name of the kind that took it first (the dot on a sign and
     * «уже у вида „Оркестр“», spec 3.36.9); [exceptId] — the kind being edited.
     */
    fun takenSigns(kinds: List<EventKind>, exceptId: Long?): Map<KindSign, String> = kinds
        .filter { val ref = it.ref; ref is KindRef.Custom && ref.id != exceptId }
        .groupBy { it.look.sign }
        .mapValues { (_, holders) -> holders.minWith(compareBy<EventKind> { it.createdAtEpochMs }.thenBy { (it.ref as KindRef.Custom).id }).ownName.orEmpty() }

    /** Another kind of one's own may be made: fewer than 20 there (spec 5.28) — the twentieth leaves no tile «Свой вид». */
    fun canAddOwn(kinds: List<EventKind>, config: EventsConfig): Boolean = kinds.count { it.ref is KindRef.Custom } < config.maxCustomKinds
}
