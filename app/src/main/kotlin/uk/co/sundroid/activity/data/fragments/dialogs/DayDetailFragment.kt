package uk.co.sundroid.activity.data.fragments.dialogs

import android.app.Fragment
import android.os.Bundle
import android.view.View
import android.support.design.widget.TabLayout
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.app.FragmentManager
import uk.co.sundroid.R
import uk.co.sundroid.activity.data.fragments.*


class DayDetailFragment : AbstractDayFragment() {

    override val layout: Int
        get() = R.layout.frag_data_daydetail

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById(R.id.viewPager) as ViewPager

        viewPager.adapter = DayDetailTabsAdapter(fragmentManager)


        val tabLayout = view.findViewById(R.id.tabLayout) as TabLayout
        tabLayout.setupWithViewPager(viewPager)
    }

    override fun updateData(view: View) {


    }

    class DayDetailTabsAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        override fun getCount(): Int {
            return 4
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                1 -> "Moon"
                2 -> "Planets"
                3 -> "Events"
                else -> "Sun"
            }
        }

        override fun getItem(position: Int): Fragment {
            return when(position) {
                1 -> DayDetailMoonFragment()
                2 -> DayDetailPlanetsFragment()
                3 -> DayDetailEventsFragment()
                else -> DayDetailSunFragment()
            }
        }
    }
}