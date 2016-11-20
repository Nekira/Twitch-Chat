package ru.ifmo.android_2016.irc;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ru.ifmo.android_2016.irc.client.Channel;
import ru.ifmo.android_2016.irc.client.Client;
import ru.ifmo.android_2016.irc.client.ClientService;
import ru.ifmo.android_2016.irc.client.ClientSettings;
import ru.ifmo.android_2016.irc.client.ServerList;

import static ru.ifmo.android_2016.irc.client.ClientService.SERVER_ID;

public class ChatActivity extends AppCompatActivity
        implements ClientService.OnConnectedListener, Client.Callback, NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = ChatActivity.class.getSimpleName();

    EditText typeMessage;
    private long id = 0;
    private int keyboardHeight;
    private ClientSettings clientSettings;
    @Nullable
    Client client;
    private ViewPagerAdapter viewPagerAdapter;
    ViewPager viewPager, emotesViewPager;
    Toolbar toolbar;
    private boolean spamMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            id = savedInstanceState.getLong("Id");
            clientSettings = ServerList.getInstance().get(id);
            client = ClientService.getClient(id);
        } else {
            load();
        }

        initView();


        // Determine keyboard height
        LinearLayout ll = (LinearLayout) findViewById(R.id.root_view);
        keyboardHeight = 550;
        ll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            ll.getWindowVisibleDisplayFrame(r);

            int screenHeight = ll.getRootView().getHeight();
            int heightDifference = screenHeight - (r.bottom - r.top);
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                heightDifference -= getResources().getDimensionPixelSize(resourceId);
            }
            if (heightDifference > 400) {
                keyboardHeight = heightDifference;
            }
// zaebal etot log
//            Log.d("Keyboard Size", "Size: " + heightDifference);
        });


        typeMessage.setOnTouchListener(((view, motionEvent) -> {
            if (isEmotesShowing())
                closeEmotes();
            return false;
        }));

        findViewById(R.id.send).setOnClickListener(v -> {
            Log.d(TAG, String.valueOf(viewPager.getCurrentItem()));
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (!TextUtils.isEmpty(typeMessage.getText()))
                viewPagerAdapter.channels.get(viewPager.getCurrentItem())
                        .send(typeMessage.getText().toString());
            if (!spamMode) typeMessage.setText("");
        });

        viewPager.setAdapter(viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager()));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                closeEmotes();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.open, R.string.close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        if (client != null) {
            client.attachUi(this);
            onChannelChange();
        }

    }


    private void initView() {
        setContentView(R.layout.activity_chat_navigation);
        typeMessage = (EditText) findViewById(R.id.text_message);
        viewPager = (ViewPager) findViewById(R.id.viewPager);
        emotesViewPager = (ViewPager) findViewById(R.id.emotes_viewpager);
    }

    private void load() {
        id = getIntent().getLongExtra(SERVER_ID, 0);
        clientSettings = ServerList.getInstance().get(id);

        ClientService.startClient(this, id);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("Id", id);
    }

    public void onEmotesShowClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        if (isEmotesShowing()) {
            closeEmotes();
            return;
        }
        if (client == null || client.getChannelList() == null) {
            Toast.makeText(this, "Client loading, please wait", Toast.LENGTH_SHORT).show();
            return;
        }
        emotesViewPager.setAdapter(new EmotesViewPagerAdapter(getSupportFragmentManager()));

        emotesViewPager.setVisibility(View.VISIBLE);
        emotesViewPager.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardHeight));

    }


    private void closeEmotes() {
        emotesViewPager.setVisibility(View.GONE);
    }

    private boolean isEmotesShowing() {
        return emotesViewPager.getVisibility() == View.VISIBLE;
    }

    public void onClearClick(View view) {
        typeMessage.setText("");
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Menu menu = ((NavigationView) findViewById(R.id.nav_view)).getMenu();
        viewPager.setCurrentItem(item.getItemId());
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setChecked(false);
        }
        item.setChecked(true);
        ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
        return true;
    }


    class EmotesViewPagerAdapter extends ViewPagerAdapter {


        EmotesViewPagerAdapter(FragmentManager supportFragmentManager) {
            super(supportFragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            return EmoteScrollViewFragment.newInstance(position == 0 ? "twitch" : "bttv");
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        } else if (isEmotesShowing()) {
            closeEmotes();
            return;
        }
        ClientService.stopClient(clientSettings.getId());
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (client != null) client.detachUi();
        super.onDestroy();
    }

    @Override
    @UiThread
    public void onConnected(final Client client) {
        ChatActivity.this.client = client;
        client.attachUi(this);
        onChannelChange();
    }

    @Override
    @UiThread
    public void onChannelChange() {
        Log.d(TAG, "onChannelChange");
        viewPagerAdapter.channels.clear();
        viewPagerAdapter.channels.addAll(client.getChannelList());
        //Stream.of(client.getChannelList()).forEach(c -> Log.d(TAG, c.getName()));
        viewPagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(1);
        int i = 0;
        Menu menu = ((NavigationView) findViewById(R.id.nav_view)).getMenu();
        menu.removeGroup(0);
        for (Channel ch : client.getChannelList()) {
            menu.add(0, i++, Menu.CATEGORY_CONTAINER, getChannelName(ch))
                    .setIcon(i == 1 ? android.R.drawable.ic_dialog_info : android.R.drawable.stat_notify_chat)
                    .setCheckable(true);
        }
        menu.getItem(menu.size() == 1 ? 0 : 1).setChecked(true);

    }

    private String getChannelName(Channel channel) {
        String name = channel.getName();
        return name.charAt(0) == '#' ? Character.toUpperCase(name.charAt(1)) + name.substring(2) : name;
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private List<Channel> channels = new ArrayList<>();

        ViewPagerAdapter(FragmentManager supportFragmentManager) {
            super(supportFragmentManager);
        }


        @Override
        public Fragment getItem(int position) {
            return ChatFragment.newInstance(id, channels.get(position).getName());
        }

        @Override
        public int getCount() {
            return channels.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return channels.get(position).getName();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, Menu.FIRST, "Clear type message after send")
                .setCheckable(true)
                .setChecked(true)
                .setOnMenuItemClickListener(menuItem -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    spamMode = !menuItem.isChecked();
                    return false;
                });
        return true;
    }
}
