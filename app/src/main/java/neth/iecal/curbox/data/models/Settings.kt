package neth.iecal.curbox.data.models

data class Settings(
    val blockedAppGroups: List<AppGroup> = listOf(),
    val manualFocusGroups: List<ManualFocusGroup> = listOf(),
    val autoDndGroups: List<AutoDndGroup> = listOf(),
    /**
     * Stores info about active manual focus mode.
     * Format Pair<GroupId?, system ms when it ends>.
     * Set group id as null when no active focus mode is running
     */
    val activeManualFocusGroupId: Pair<String?, Long> = Pair(null, 0),

    val reelBlockerConfig: ReelBlocker = ReelBlocker(),
    val keywordBlockerConfig: KeywordBlocker = KeywordBlocker(
        isActive = true,
        keywordGroups = listOf(
            KeywordGroup(
                id = "porn_default",
                name = "Pornographie",
                selectedKeywords = listOf(
                    "*porn*", "*xxx*", "*hentai*", "*nsfw*", "*sexe*", "*sexcam*",
                    "*xvideos*", "*xnxx*", "*xhamster*", "*redtube*", "*youporn*",
                    "*spankbang*", "*brazzers*", "*onlyfans*", "*chaturbate*", "*stripchat*",
                    "*bongacams*", "*camsoda*", "*rule34*", "*nhentai*", "*fapello*",
                    "*motherless*", "*eporner*", "*youjizz*", "*camwhores*",
                    "beeg", "tnaflix", "txxx", "tube8", "hclips", "porntrex",
                    "jacquieetmichel", "tukif", "dorcel", "mrsexe"
                ),
                blockingType = AppBlockingType.OnOpen,
                isActive = true
            )
        )
    ),
    val isReelCounterOn: Boolean = true,
    val grayscaleGroups: List<GrayscaleGroup> = listOf(),
    val usageTrackerIgnoredApps: List<String> = listOf(),
    val mindfulMessageConfig: MindfulMessageConfig = MindfulMessageConfig(),
    val uiHiderConfig: UiHiderConfig = UiHiderConfig(),
    val reelCounterOverlayConfig: ReelCounterOverlayConfig = ReelCounterOverlayConfig(),
    val nextWebsiteRecheckTime: Long = 0L
)
