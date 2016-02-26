/*
 * Mr.Mantou - On the importance of taste
 * Copyright (C) 2015  XiNGRZ <xxx@oxo.ooo>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ooo.oxo.mr;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableArrayList;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.jakewharton.rxbinding.support.v4.widget.RxSwipeRefreshLayout;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;
import com.umeng.analytics.MobclickAgent;

import java.util.List;
import java.util.Map;

import ooo.oxo.mr.databinding.MainActivityBinding;
import ooo.oxo.mr.model.Image;
import ooo.oxo.mr.rx.RxAVQuery;
import ooo.oxo.mr.rx.RxList;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends RxAppCompatActivity implements MainAdapter.Listener {

    private static final String TAG = "MainActivity";

    private ObservableArrayList<Image> images;

    private MainActivityBinding binding;

    private Bundle reenterState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity);

        setSupportActionBar(binding.toolbar);

        images = MrSharedState.getInstance().getImages();

        Glide.get(this).setMemoryCategory(MemoryCategory.HIGH);
        binding.content.setAdapter(new MainAdapter(this, images, Glide.with(this), this));

        setExitSharedElementCallback(new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (reenterState != null) {
                    int index = reenterState.getInt("index", 0);

                    Image image = images.get(index);

                    MainAdapter.ViewHolder holder = (MainAdapter.ViewHolder) binding.content
                            .findViewHolderForLayoutPosition(index);

                    sharedElements.clear();

                    if (holder != null && holder.binding != null) {
                        sharedElements.put(String.format("%s.image", image.getObjectId()), holder.binding.image);
                    }

                    reenterState = null;
                }
            }
        });

        RxAVQuery.find(Image.all())
                .compose(bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(() -> binding.refresher.setRefreshing(true))
                .doOnCompleted(() -> binding.refresher.setRefreshing(false))
                .subscribe(RxList.appendTo(images), this::showError);

        RxSwipeRefreshLayout.refreshes(binding.refresher)
                .compose(bindToLifecycle())
                .map(avoid -> images.get(0))
                .observeOn(Schedulers.io())
                .flatMap(latest -> RxAVQuery.find(Image.since(latest)))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(avoid -> binding.refresher.setRefreshing(false))
                .doOnNext(avoid -> binding.content.smoothScrollToPosition(0))
                .retry()
                .subscribe(RxList.prependTo(images));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.content.setAdapter(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    private void showError(Throwable error) {
        Log.e(TAG, "error", error);
        Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onImageClick(MainAdapter.ViewHolder holder) {
        final Image image = images.get(holder.getLayoutPosition());

        final Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("index", holder.getAdapterPosition());
        intent.putExtra("thumbnail", image.getUrl(holder.binding.image.getWidth()));

        final ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, holder.binding.image, String.format("%s.image", image.getObjectId()));

        startActivity(intent, options.toBundle());
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);

        supportPostponeEnterTransition();

        reenterState = new Bundle(data.getExtras());

        binding.content.scrollToPosition(reenterState.getInt("index", 0));
        binding.content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                binding.content.getViewTreeObserver().removeOnPreDrawListener(this);
                supportStartPostponedEnterTransition();
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
