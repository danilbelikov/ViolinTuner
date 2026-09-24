package com.violinjourney.app.feature.home

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.Composable
import com.violinjourney.app.core.ui.components.LocalMessages
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.shop_bought
import org.jetbrains.compose.resources.getString

@Composable
fun HomeRoute(
    view: HomeView,
    onOpenShop: () -> Unit,
    onOpenArrange: () -> Unit,
    onOpenHouses: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenJourney: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shop by rememberUpdatedState(onOpenShop)
    val arrange by rememberUpdatedState(onOpenArrange)
    val houses by rememberUpdatedState(onOpenHouses)
    val home by rememberUpdatedState(onOpenHome)
    val close by rememberUpdatedState(onClose)
    val journey by rememberUpdatedState(onOpenJourney)
    val reduce = rememberAnimationsRemoved()

    LaunchedEffect(viewModel, reduce) { viewModel.onIntent(HomeIntent.ReduceMotionChanged(reduce)) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    HomeEffect.Close -> close()
                    HomeEffect.OpenShop -> shop()
                    HomeEffect.OpenArrange -> arrange()
                    HomeEffect.OpenHouses -> houses()
                    HomeEffect.OpenHome -> home()
                    HomeEffect.OpenJourney -> journey()
                    is HomeEffect.ShowBought -> HomeTexts.itemNames[effect.itemId]?.let { name ->
                        messages.show(getString(Res.string.shop_bought, getString(name)))
                    }
                }
            }
        }
    }
    // «назад» folds the card or the trying-on first; the film of moving in has no way out
    BackHandler(enabled = ui.card != null || ui.tryOn != null || ui.houseCard != null || ui.moving != null || ui.fullscreen) { viewModel.onIntent(HomeIntent.BackClicked) }
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        when (view) {
            HomeView.MAIN -> HomeScreen(ui, viewModel::onIntent, modifier)
            HomeView.SHOP -> ShopScreen(ui, viewModel::onIntent, modifier)
            HomeView.ARRANGE -> ArrangeScreen(ui, viewModel::onIntent, modifier)
            HomeView.HOUSES -> HousesScreen(ui, viewModel::onIntent, modifier)
        }
    }
}
