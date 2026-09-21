package com.example.violintuner.feature.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.core.ui.motion.rememberAnimationsRemoved

@Composable
fun HomeRoute(
    view: HomeView,
    onOpenShop: () -> Unit,
    onOpenArrange: () -> Unit,
    onOpenHouses: () -> Unit,
    onOpenHome: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shop by rememberUpdatedState(onOpenShop)
    val arrange by rememberUpdatedState(onOpenArrange)
    val houses by rememberUpdatedState(onOpenHouses)
    val home by rememberUpdatedState(onOpenHome)
    val close by rememberUpdatedState(onClose)
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
                    is HomeEffect.ShowBought -> HomeTexts.itemNames[effect.itemId]?.let { name ->
                        Toast.makeText(context, resources.getString(R.string.shop_bought, resources.getString(name)), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // «назад» folds the card or the trying-on first; the film of moving in has no way out
    BackHandler(enabled = ui.card != null || ui.tryOn != null || ui.houseCard != null || ui.moving != null) { viewModel.onIntent(HomeIntent.BackClicked) }
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        when (view) {
            HomeView.MAIN -> HomeScreen(ui, viewModel::onIntent, modifier)
            HomeView.SHOP -> ShopScreen(ui, viewModel::onIntent, modifier)
            HomeView.ARRANGE -> ArrangeScreen(ui, viewModel::onIntent, modifier)
            HomeView.HOUSES -> HousesScreen(ui, viewModel::onIntent, modifier)
        }
    }
}
