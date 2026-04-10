package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.frienddebt.R;
import com.example.frienddebt.ui.fragment.MoneyFragment;
import com.example.frienddebt.ui.fragment.HomeFragment;
import com.example.frienddebt.ui.fragment.NotesFragment;
import com.example.frienddebt.ui.fragment.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG_HOME = "nav_home";
    private static final String TAG_MONEY = "nav_money";
    private static final String TAG_NOTES = "nav_notes";
    private static final String TAG_PROFILE = "nav_profile";

    private Fragment homeFragment;
    private Fragment moneyFragment;
    private Fragment notesFragment;
    private Fragment profileFragment;
    private Fragment activeFragment;
    private FragmentManager fm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode immediately before super.onCreate to prevent flashes
        android.content.SharedPreferences sp = getSharedPreferences("NexaPrefs", android.content.Context.MODE_PRIVATE);
        boolean isDarkMode = sp.getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        fm = getSupportFragmentManager();
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            moneyFragment = new MoneyFragment();
            notesFragment = new NotesFragment();
            profileFragment = new ProfileFragment();
            activeFragment = homeFragment;

            fm.beginTransaction()
                    .add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment)
                    .add(R.id.fragment_container, notesFragment, TAG_NOTES).hide(notesFragment)
                    .add(R.id.fragment_container, moneyFragment, TAG_MONEY).hide(moneyFragment)
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commit();
        } else {
            homeFragment = fm.findFragmentByTag(TAG_HOME);
            moneyFragment = fm.findFragmentByTag(TAG_MONEY);
            notesFragment = fm.findFragmentByTag(TAG_NOTES);
            profileFragment = fm.findFragmentByTag(TAG_PROFILE);

            if (homeFragment == null) homeFragment = new HomeFragment();
            if (moneyFragment == null) moneyFragment = new MoneyFragment();
            if (notesFragment == null) notesFragment = new NotesFragment();
            if (profileFragment == null) profileFragment = new ProfileFragment();

            activeFragment = findVisibleFragment();
            if (activeFragment == null) {
                activeFragment = homeFragment;
            }

            boolean needsCommit = false;
            FragmentTransaction tx = fm.beginTransaction();

            if (!homeFragment.isAdded()) {
                tx.add(R.id.fragment_container, homeFragment, TAG_HOME);
                needsCommit = true;
            }
            if (!moneyFragment.isAdded()) {
                tx.add(R.id.fragment_container, moneyFragment, TAG_MONEY).hide(moneyFragment);
                needsCommit = true;
            }
            if (!notesFragment.isAdded()) {
                tx.add(R.id.fragment_container, notesFragment, TAG_NOTES).hide(notesFragment);
                needsCommit = true;
            }
            if (!profileFragment.isAdded()) {
                tx.add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment);
                needsCommit = true;
            }

            if (homeFragment.isAdded() && activeFragment != homeFragment) {
                tx.hide(homeFragment);
            }
            if (moneyFragment.isAdded() && activeFragment != moneyFragment) {
                tx.hide(moneyFragment);
            }
            if (notesFragment.isAdded() && activeFragment != notesFragment) {
                tx.hide(notesFragment);
            }
            if (profileFragment.isAdded() && activeFragment != profileFragment) {
                tx.hide(profileFragment);
            }

            if (activeFragment != null && activeFragment.isAdded()) {
                tx.show(activeFragment);
            }

            if (needsCommit) {
                tx.commit();
            }
        }

        navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    fm.beginTransaction().hide(activeFragment).show(homeFragment).commit();
                    activeFragment = homeFragment;
                    if (homeFragment instanceof HomeFragment) {
                        ((HomeFragment) homeFragment).loadData();
                    }
                    return true;
                } else if (itemId == R.id.nav_money) {
                    fm.beginTransaction().hide(activeFragment).show(moneyFragment).commit();
                    activeFragment = moneyFragment;
                    if (moneyFragment instanceof MoneyFragment) {
                        ((MoneyFragment) moneyFragment).loadBalanceData();
                    }
                    return true;
                } else if (itemId == R.id.nav_notes) {
                    fm.beginTransaction().hide(activeFragment).show(notesFragment).commit();
                    activeFragment = notesFragment;
                    if (notesFragment instanceof NotesFragment) {
                        ((NotesFragment) notesFragment).loadNotes();
                    }
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    fm.beginTransaction().hide(activeFragment).show(profileFragment).commit();
                    activeFragment = profileFragment;
                    return true;
                }
                return false;
            }
        });

        if (activeFragment == moneyFragment) {
            navView.setSelectedItemId(R.id.nav_money);
        } else if (activeFragment == notesFragment) {
            navView.setSelectedItemId(R.id.nav_notes);
        } else if (activeFragment == profileFragment) {
            navView.setSelectedItemId(R.id.nav_profile);
        } else {
            navView.setSelectedItemId(R.id.nav_home);
        }
    }

    /**
     * Switch to a specific bottom nav tab programmatically.
     */
    public void selectTab(int itemId) {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(itemId);
    }

    /**
     * Switch to the Money tab and open a specific sub-tab.
     * @param subTabIndex 0=Personal, 1=Groups, 2=Overview
     */
    public void selectMoneyTab(int subTabIndex) {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(R.id.nav_money);

        // Post delayed to ensure the fragment is visible before switching sub-tab
        navView.postDelayed(() -> {
            if (moneyFragment instanceof MoneyFragment) {
                ((MoneyFragment) moneyFragment).switchToTab(subTabIndex);
            }
        }, 100);
    }

    private Fragment findVisibleFragment() {
        if (homeFragment != null && !homeFragment.isHidden()) return homeFragment;
        if (moneyFragment != null && !moneyFragment.isHidden()) return moneyFragment;
        if (notesFragment != null && !notesFragment.isHidden()) return notesFragment;
        if (profileFragment != null && !profileFragment.isHidden()) return profileFragment;
        return null;
    }
}
