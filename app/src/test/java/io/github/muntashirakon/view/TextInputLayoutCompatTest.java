// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TextInputLayoutCompatTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.setTheme(io.github.muntashirakon.ui.R.style.Theme_AppTheme);
    }

    @Test
    public void fromTextInputEditTextFindsAncestorTextInputLayoutAcrossNestedContainers() {
        TextInputLayout textInputLayout = new TextInputLayout(mContext);
        LinearLayout intermediateLayout = new LinearLayout(mContext);
        FrameLayout innerFrame = new FrameLayout(mContext);
        TextInputEditText editText = new TextInputEditText(mContext);

        innerFrame.addView(editText);
        intermediateLayout.addView(innerFrame);
        textInputLayout.addView(intermediateLayout);

        TextInputLayout result = TextInputLayoutCompat.fromTextInputEditText(editText);
        assertNotNull("Should find ancestor TextInputLayout across intermediate containers", result);
        assertEquals(textInputLayout, result);
    }

    @Test
    public void fromTextInputEditTextReturnsNullWhenNoTextInputLayoutAncestor() {
        LinearLayout container = new LinearLayout(mContext);
        TextInputEditText editText = new TextInputEditText(mContext);
        container.addView(editText);

        TextInputLayout result = TextInputLayoutCompat.fromTextInputEditText(editText);
        assertNull("Should return null instead of throwing ClassCastException", result);
    }

    @Test
    public void fixEndIconPreservesLinearLayoutLayoutParamsWithoutClassCast() {
        TextInputLayout layout = new TextInputLayout(mContext);
        TextInputEditText editText = new TextInputEditText(mContext);
        layout.addView(editText);

        AppCompatImageButton endIcon = new AppCompatImageButton(mContext);
        endIcon.setId(com.google.android.material.R.id.text_input_end_icon);
        LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(10, 10);
        endIcon.setLayoutParams(linearParams);
        layout.addView(endIcon);

        TextInputLayoutCompat.fixEndIcon(layout);

        assertTrue("End icon params must remain LinearLayout.LayoutParams to prevent ClassCastException",
                endIcon.getLayoutParams() instanceof LinearLayout.LayoutParams);
    }
}
