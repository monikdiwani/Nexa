package com.example.frienddebt.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
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

import com.example.frienddebt.utils.StatusBarUtil;
import com.example.frienddebt.utils.UserProfileHelper;

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

    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode immediately before super.onCreate to prevent flashes
        android.content.SharedPreferences sp = getSharedPreferences("NexaPrefs", android.content.Context.MODE_PRIVATE);
        boolean isDarkMode = sp.getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);

        // Save current user ID to SharedPreferences for background notification receiver access
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new android.content.Intent(this, Login.class));
            finish();
            return;
        }

        // Run Recurring Engine
        com.example.frienddebt.utils.RecurringEngine.processRecurringItems(this, auth.getCurrentUser().getUid());

        // Ensure user profile is stored in Firestore so names appear everywhere (groups, settle-up, etc.)
        com.google.firebase.auth.FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            UserProfileHelper.saveProfile(
                currentUser.getUid(),
                currentUser.getDisplayName(),
                currentUser.getEmail()
            );
        }

        sp.edit().putString("user_id", auth.getCurrentUser().getUid()).apply();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        StatusBarUtil.applyStatusBarPadding(this);

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
        // Back button: go to Home first, then double-press to exit
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BottomNavigationView navView = findViewById(R.id.bottom_navigation);
                if (navView.getSelectedItemId() != R.id.nav_home) {
                    navView.setSelectedItemId(R.id.nav_home);
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastBackPressTime < 2000) {
                        // Second press within 2s — exit
                        finish();
                    } else {
                        lastBackPressTime = now;
                        Toast.makeText(DashboardActivity.this,
                                "Press back again to exit", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    public void selectTab(int itemId) {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(itemId);
    }

    /**
     * Switch to the Money tab.
     */
    public void selectMoneyTab() {
        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setSelectedItemId(R.id.nav_money);
    }

    private Fragment findVisibleFragment() {
        if (homeFragment != null && !homeFragment.isHidden()) return homeFragment;
        if (moneyFragment != null && !moneyFragment.isHidden()) return moneyFragment;
        if (notesFragment != null && !notesFragment.isHidden()) return notesFragment;
        if (profileFragment != null && !profileFragment.isHidden()) return profileFragment;
        return null;
    }




    @Override
    public void startActivity(android.content.Intent intent) {
        super.startActivity(intent);
        com.example.frienddebt.utils.AnimationHelper.applyStartTransition(this, intent);
    }

}
