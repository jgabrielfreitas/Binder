package com.vodafone.binder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.vodafone.binding.Binder;
import com.vodafone.binding.annotations.SubscribeTo;
import com.vodafone.binding.annotations.SubscriptionsFactory;

import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.Subject;

@SubscriptionsFactory(SubViewModel.class)
public class SubActivity extends AppCompatActivity {

    private Binder<SubViewModel> binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub);
        binder = Binder
                .bind(this)
                .toNewSubscriptionsFactory();
    }

    @SubscribeTo("stringSubject")
    Disposable stringDisposable(Subject<String> subject) {
        return subject.subscribe(System.err::print);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binder.unbind();
    }
}
