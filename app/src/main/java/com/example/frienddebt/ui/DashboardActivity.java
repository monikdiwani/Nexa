package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.frienddebt.R;
import com.example.frienddebt.ui.fragment.CashbookFragment;
import com.example.frienddebt.ui.fragment.FairShareFragment;
import com.example.frienddebt.ui.fragment.HomeFragment;
import com.example.frienddebt.ui.fragment.NotesFragment;
import com.example.frienddebt.ui.fragment.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG_HOME = "nav_home";
    private static final String TAG_CASHBOOK = "nav_cashbook";
    private static final String TAG_GROUPS = "nav_groups";
    private static final String TAG_NOTES = "nav_notes";
    private static final String TAG_PROFILE = "nav_profile";

    private Fragment homeFragment;
    private Fragment cashbookFragment;
    private Fragment fairShareFragment;
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
            cashbookFragment = new CashbookFragment();
            fairShareFragment = new FairShareFragment();
            notesFragment = new NotesFragment();
            profileFragment = new ProfileFragment();
            activeFragment = homeFragment;

            fm.beginTransaction()
                    .add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment)
                    .add(R.id.fragment_container, notesFragment, TAG_NOTES).hide(notesFragment)
                    .add(R.id.fragment_container, fairShareFragment, TAG_GROUPS).hide(fairShareFragment)
                    .add(R.id.fragment_container, cashbookFragment, TAG_CASHBOOK).hide(cashbookFragment)
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commit();
        } else {
            homeFragment = fm.findFragmentByTag(TAG_HOME);
            cashbookFragment = fm.findFragmentByTag(TAG_CASHBOOK);
            fairShareFragment = fm.findFragmentByTag(TAG_GROUPS);
            notesFragment = fm.findFragmentByTag(TAG_NOTES);
            profileFragment = fm.findFragmentByTag(TAG_PROFILE);

            if (homeFragment == null) homeFragment = new HomeFragment();
            if (cashbookFragment == null) cashbookFragment = new CashbookFragment();
            if (fairShareFragment == null) fairShareFragment = new FairShareFragment();
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
            if (!cashbookFragment.isAdded()) {
                tx.add(R.id.fragment_container, cashbookFragment, TAG_CASHBOOK).hide(cashbookFragment);
                needsCommit = true;
            }
            if (!fairShareFragment.isAdded()) {
                tx.add(R.id.fragment_container, fairShareFragment, TAG_GROUPS).hide(fairShareFragment);
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
            if (cashbookFragment.isAdded() && activeFragment != cashbookFragment) {
                tx.hide(cashbookFragment);
            }
            if (fairShareFragment.isAdded() && activeFragment != fairShareFragment) {
                tx.hide(fairShareFragment);
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
                } else if (itemId == R.id.nav_cashbook) {
                    fm.beginTransaction().hide(activeFragment).show(cashbookFragment).commit();
                    activeFragment = cashbookFragment;
                    if (cashbookFragment instanceof CashbookFragment) {
                        ((CashbookFragment) cashbookFragment).loadData();
                    }
                    return true;
                } else if (itemId == R.id.nav_groups) {
                    fm.beginTransaction().hide(activeFragment).show(fairShareFragment).commit();
                    activeFragment = fairShareFragment;
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

        if (activeFragment == cashbookFragment) {
            navView.setSelectedItemId(R.id.nav_cashbook);
        } else if (activeFragment == fairShareFragment) {
            navView.setSelectedItemId(R.id.nav_groups);
        } else if (activeFragment == notesFragment) {
            navView.setSelectedItemId(R.id.nav_notes);
        } else if (activeFragment == profileFragment) {
            navView.setSelectedItemId(R.id.nav_profile);
        } else {
            navView.setSelectedItemId(R.id.nav_home);
        }
    }

    public void selectTab(int itemId) {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(itemId);
    }

    private Fragment findVisibleFragment() {
        if (homeFragment != null && !homeFragment.isHidden()) return homeFragment;
        if (cashbookFragment != null && !cashbookFragment.isHidden()) return cashbookFragment;
        if (fairShareFragment != null && !fairShareFragment.isHidden()) return fairShareFragment;
        if (notesFragment != null && !notesFragment.isHidden()) return notesFragment;
        if (profileFragment != null && !profileFragment.isHidden()) return profileFragment;
        return null;
    }
}
