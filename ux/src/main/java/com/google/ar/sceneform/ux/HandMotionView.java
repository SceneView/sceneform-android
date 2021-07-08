/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */
package com.google.ar.sceneform.ux;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;

import androidx.appcompat.widget.AppCompatImageView;


/**
 * This view contains the hand motion instructions with animation.
 */
public class HandMotionView extends AppCompatImageView {
    private HandMotionAnimation animation;
    private static final long ANIMATION_SPEED_MS = 2500;

    public HandMotionView(Context context) {
        super(context);
    }

    public HandMotionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        clearAnimation();

        FrameLayout container = ((Activity) getContext()).findViewById(R.id.handMotionLayout);

        animation = new HandMotionAnimation(container, this);
        animation.setRepeatCount(Animation.INFINITE);
        animation.setDuration(ANIMATION_SPEED_MS);
        animation.setStartOffset(1000);

        startAnimation(animation);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        updateVisibility();
    }

    private void updateVisibility() {
        if (getVisibility() == View.VISIBLE) {
            startAnimation(animation);
        } else {
            clearAnimation();
        }
    }
}
