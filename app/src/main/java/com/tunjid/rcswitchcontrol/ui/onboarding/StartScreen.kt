package com.tunjid.rcswitchcontrol.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunjid.globalui.ToolbarIcon
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.di.ComposeDagger
import com.tunjid.rcswitchcontrol.onboarding.Input
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme
import com.tunjid.rcswitchcontrol.ui.theme.colorAccent
import com.tunjid.rcswitchcontrol.ui.theme.darkText

@Composable
fun StartScreen() {
    val uiStateMachine = ComposeDagger.current.appComponent.uiStateMachine

    DisposableEffect(true) {
        uiStateMachine.accept(Mutation {
            UiState(
                toolbarShows = true,
                toolbarTitle = "Home Hub",
            )
        })
        onDispose { uiStateMachine.accept(Mutation { copy(toolbarMenuClickListener = {}) }) }
    }
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.splash_message),
            color = darkText
        )
        Image(
            modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
            painter = painterResource(id = R.drawable.ic_settings_remote_grey_24dp),
            contentDescription = stringResource(id = R.string.app_name)
        )
        Row {
            TextButton(
                modifier = Modifier
                    .weight(1f),
                onClick = { },
                content = {
                    Text(
                        text = stringResource(id = R.string.server),
                        color = colorAccent
                    )
                })
            TextButton(
                modifier = Modifier
                    .weight(1f),
                onClick = { },
                content = {
                    Text(
                        text = stringResource(id = R.string.client),
                        color = colorAccent
                    )
                })
        }
    }

}

@Preview(name = "Start screen")
@Composable
private fun Preview() {
    AppTheme {
        StartScreen()
    }
}