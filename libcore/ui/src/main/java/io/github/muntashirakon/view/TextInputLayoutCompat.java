// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.android.material.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// TODO: 26/6/23 Make it a full replacement for TextInputLayout which is purposefully made for devs to suffer
public final class TextInputLayoutCompat {
    @Nullable
    public static TextInputLayout fromTextInputEditText(@NonNull TextInputEditText editText) {
        ViewParent parent = editText.getParent();
        while (parent instanceof View) {
            if (parent instanceof TextInputLayout) {
                return (TextInputLayout) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    public static void fixEndIcon(@NonNull TextInputLayout layout) {
        EditText editText = layout.getEditText();
        if (editText == null) {
            // Invalid
            return;
        }
        layout.post(() -> {
            AppCompatImageButton endIconView = layout.findViewById(R.id.text_input_end_icon);
            if (endIconView == null) {
                return;
            }
            int endIconSize = layout.getEndIconMinSize();
            int width = editText.getMinimumHeight(); // For consistency
            int newHeight = endIconSize > 0 ? endIconSize : width;
            int newWidth = Math.max(width, newHeight);

            ViewGroup.LayoutParams existingParams = endIconView.getLayoutParams();
            if (existingParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) existingParams;
                lp.width = newWidth;
                lp.height = newHeight;
                lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                endIconView.setLayoutParams(lp);
            } else if (existingParams instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) existingParams;
                lp.width = newWidth;
                lp.height = newHeight;
                lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                endIconView.setLayoutParams(lp);
            } else if (existingParams != null) {
                existingParams.width = newWidth;
                existingParams.height = newHeight;
                endIconView.setLayoutParams(existingParams);
            } else {
                ViewParent parent = endIconView.getParent();
                if (parent instanceof LinearLayout) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(newWidth, newHeight);
                    lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                    endIconView.setLayoutParams(lp);
                } else {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(newWidth, newHeight);
                    lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                    endIconView.setLayoutParams(lp);
                }
            }
            endIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        });
    }
}
