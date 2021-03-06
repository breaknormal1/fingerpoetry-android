package com.klisly.bookbox.ui.fragment.novel;

import android.app.NotificationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.klisly.bookbox.BookBoxApplication;
import com.klisly.bookbox.Constants;
import com.klisly.bookbox.R;
import com.klisly.bookbox.api.BookRetrofit;
import com.klisly.bookbox.api.NovelApi;
import com.klisly.bookbox.domain.ApiResult;
import com.klisly.bookbox.logic.AccountLogic;
import com.klisly.bookbox.model.Article;
import com.klisly.bookbox.model.Chapter;
import com.klisly.bookbox.subscriber.AbsSubscriber;
import com.klisly.bookbox.subscriber.ApiException;
import com.klisly.bookbox.ui.OuterFragment;
import com.klisly.bookbox.ui.base.BaseBackFragment;
import com.klisly.bookbox.utils.ChapterParser;
import com.klisly.bookbox.utils.DateUtil;
import com.klisly.bookbox.utils.ShareUtil;
import com.klisly.bookbox.utils.ToastHelper;
import com.klisly.common.StringUtils;
import com.material.widget.CircularProgress;
import com.qq.e.ads.banner.ADSize;
import com.qq.e.ads.banner.AbstractBannerADListener;
import com.qq.e.ads.banner.BannerView;
import com.qq.e.comm.util.AdError;

import butterknife.Bind;
import butterknife.ButterKnife;
import cn.sharesdk.framework.ShareSDK;
import cn.sharesdk.onekeyshare.OnekeyShare;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static android.content.Context.NOTIFICATION_SERVICE;

public class ChapterFragment extends BaseBackFragment implements Toolbar.OnMenuItemClickListener {
    private static final String ARG_CONTENT = "arg_article";
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.webView)
    WebView tvContent;
    @Bind(R.id.bannerContainer)
    ViewGroup bannerContainer;
    BannerView bv;
    private Chapter mData;
    private NovelApi novelApi = BookRetrofit.getInstance().getNovelApi();
    NotificationManager manager;
    private Menu menu;

    public static ChapterFragment newInstance(Article article) {
        ChapterFragment fragment = new ChapterFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CONTENT, article);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mData = (Chapter) args.getSerializable(ARG_CONTENT);
        }
        manager = (NotificationManager) BookBoxApplication.getInstance().getSystemService(NOTIFICATION_SERVICE);

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail, container, false);
        ButterKnife.bind(this, view);
        initView(view);
        // 取消该篇小说的更新
        manager.cancel(mData.getId().hashCode());
        return view;
    }

    private void loadContent() {

        if (!StringUtils.isEmpty(mData.getContent())) {
            updateData();
            return;
        }
        novelApi.fetch(mData.getId(), AccountLogic.getInstance().getUserId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new AbsSubscriber<ApiResult<Chapter>>(getActivity(), false) {
                    @Override
                    protected void onError(ApiException ex) {
                        getDataFromWeb();
                    }

                    @Override
                    protected void onPermissionError(ApiException ex) {
                        getDataFromWeb();
                    }

                    @Override
                    public void onNext(ApiResult<Chapter> res) {
                        if (res.getData() != null && StringUtils.isNotEmpty(res.getData().getContent())) {
                            mData = res.getData();
                            updateData();
                        } else {
                            getDataFromWeb();
                        }
                    }
                });

    }

    private void getDataFromWeb() {
        BookBoxApplication.getInstance().getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                String content = ChapterParser.getContet(mData.getSrcUrl());
                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() == null || getActivity().isFinishing()) {
                            return;
                        }

                        if (!StringUtils.isEmpty(content)) {
                            mData.setContent(content);
                            updateData();
                        } else {
                            ToastHelper.showShortTip(R.string.get_detial_fail);
                        }
                    }
                });
            }
        });
    }

    private void updateData() {
        String html = Constants.ARTICLE_PREFIX + mData.getContent() + Constants.ARTICLE_SUFFIX;
        html = html.replace(Constants.NOVEL_END_0, "");
        html = html.replace(Constants.NOVEL_END_1, "");
        html = html.replace(Constants.NOVEL_END_2, "");
        html = html.replace(Constants.NOVEL_END_3, "");
        tvContent.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

        int w = View.MeasureSpec.makeMeasureSpec(0,
                View.MeasureSpec.UNSPECIFIED);
        int h = View.MeasureSpec.makeMeasureSpec(0,
                View.MeasureSpec.UNSPECIFIED);
        //重新测量
        tvContent.measure(w, h);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    private void initView(View view) {
        toolbar.setTitleTextAppearance(getContext(), R.style.TitleTextApperance);
        initToolbarNav(toolbar);
        toolbar.setOnMenuItemClickListener(this);


        view.findViewById(R.id.rl_action).setVisibility(View.GONE);
        tvContent.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        initBanner();
        bannerContainer.postDelayed(new Runnable() {
            @Override
            public void run() {
                bv.loadAD();
            }
        }, 1200);
    }

    @Override
    protected void initToolbarMenu(Toolbar toolbar) {
        toolbar.inflateMenu(R.menu.menu_article_pop);
        menu = toolbar.getMenu();
        menu.getItem(0).setVisible(false);
        menu.getItem(1).setVisible(false);
    }

    private void initBanner() {
        this.bv = new BannerView(getActivity(), ADSize.BANNER, Constants.QQ_APP_ID, Constants.BannerPosId);
        bv.setRefresh(30);
        bv.setADListener(new AbstractBannerADListener() {

            @Override
            public void onNoAD(AdError adError) {
                bannerContainer.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onADReceiv() {
//                // 初始化需要加载的动画资源
//                Animation animation = AnimationUtils
//                        .loadAnimation(getActivity().getApplicationContext(), R.anim.slide_in_from_top);
//                // “淡入淡出”动画。
//                // 0.0f，完全透明，
//                // 1.0f，完全不透明。
//                AlphaAnimation alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
//                alphaAnimation.setDuration(1000);
//                // 动画集。
//                AnimationSet animationSet = new AnimationSet(false);
//                animationSet.addAnimation(alphaAnimation);
//                animationSet.addAnimation(animation);
//
//                // 开始播放动画。
//                bannerContainer.startAnimation(animationSet);
            }
        });
        bannerContainer.addView(bv);
    }

    /**
     * 这里演示:
     * 比较复杂的Fragment页面会在第一次start时,导致动画卡顿
     * Fragmentation提供了onEnterAnimationEnd()方法,该方法会在 入栈动画 结束时回调
     * 所以在onCreateView进行一些简单的View初始化(比如 toolbar设置标题,返回按钮; 显示加载数据的进度条等),
     * 然后在onEnterAnimationEnd()方法里进行 复杂的耗时的初始化 (比如FragmentPagerAdapter的初始化 加载数据等)
     */
    @Override
    public void onEnterAnimationEnd(Bundle savedInstance) {
        loadContent();
        initLazyView();
    }

    private void initLazyView() {
        toolbar.setTitle(mData.getTitle());
    }

    @Override
    public void onFragmentResult(int requestCode, int resultCode, Bundle data) {
        super.onFragmentResult(requestCode, resultCode, data);
    }

    /**
     * 类似于 Activity的 onNewIntent()
     */
    @Override
    public void onNewBundle(Bundle args) {
        super.onNewBundle(args);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                shareArticle();
                break;

            case R.id.action_original:
                start(OuterFragment.newInstance(mData));
                break;

            case R.id.action_notify_setting:
                ToastHelper.showShortTip(R.string.report);
                break;
            default:
                break;
        }
        return true;
    }

    private void shareArticle() {
        if (mData == null) {
            return;
        }
        String shareUrl = "http://second.imdao.cn/chapters/" + mData.getId();
        String img = "http://second.imdao.cn/images/logo.png";
        String title = "小说更新-" + mData.getTitle();
        String desc = "小说更新-" + "\"" + mData.getTitle() + "\"" + "." + shareUrl;
        String from = getString(R.string.app_name);
        String comment = "更新内容：" + mData.getContent().substring(0, Math.min(50, mData.getContent().length()));
        ShareUtil.shareArticle(shareUrl, img, title, desc, from, comment);
    }

}
