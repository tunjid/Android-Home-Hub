package com.tunjid.rcswitchcontrol.fragments.tv

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.fragments.RecordFragment
import com.tunjid.rcswitchcontrol.utils.LifecycleDisposable
import com.tunjid.rcswitchcontrol.utils.guard
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ProtocolKey

class TvControlFragment : BrowseSupportFragment(), Navigator.TagProvider {

    private val viewModel by activityViewModels<ControlViewModel>()

    private val lifecycleDisposable = LifecycleDisposable(lifecycle)

    private lateinit var rowsAdapter: ArrayObjectAdapter

    override val stableTag: String = "ControlHeaders"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = requireContext().colorAt(R.color.fastlane_background)
        title = "Title goes here"
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter

        setOnSearchClickedListener {}
        mainFragmentRegistry.registerFragment(ControlRow::class.java, ControlRowFragmentFactory())
    }

    override fun onResume() {
        super.onResume()
        prepareEntranceTransition()
        startEntranceTransition()

        rowsAdapter.setItems(viewModel.keys.map(::ControlRow), null)

        viewModel.listen(ControlViewModel.State::class.java)
                .subscribe(this::onPayloadReceived, Throwable::printStackTrace)
                .guard(lifecycleDisposable)
    }

    private fun onPayloadReceived(state: ControlViewModel.State) {
        if (state !is ControlViewModel.State.Commands || !state.isNew) return
        rowsAdapter.setItems(viewModel.keys.map(::ControlRow), null)
    }
}

private class ControlRowFragmentFactory internal constructor()
    : BrowseSupportFragment.FragmentFactory<Fragment>() {

    override fun createFragment(rowObj: Any): Fragment {
        val row = rowObj as ControlRow

        return RecordFragment.tvCommandInstance(row.key)

    }
}

private class ControlRow(val key: ProtocolKey) : PageRow(HeaderItem(key.name.hashCode().toLong(), key.title))
