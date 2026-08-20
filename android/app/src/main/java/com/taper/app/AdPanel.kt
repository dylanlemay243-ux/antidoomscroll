package com.taper.app

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * A native ad laid out to Taper's own measure: quiet, flat, no fullscreen takeover,
 * no autoplaying sound. It reads as a card inside the pause screen rather than an
 * interruption on top of it — which is both the honest presentation and the one that
 * doesn't make the app feel like a money grab.
 *
 * Required by AdMob policy and kept deliberately: the "Ad" badge, the headline, and
 * AdChoices (drawn by the SDK). Everything else is optional and shown only if the ad
 * actually carries it.
 */
@Composable
fun NativeAdPanel(ad: NativeAd, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            fun dp(v: Int) = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
            ).toInt()

            val adView = NativeAdView(ctx)

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(AndroidColor.parseColor("#33455034"))
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val badge = TextView(ctx).apply {
                text = "AD"
                setTextColor(AndroidColor.parseColor("#8FA377"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = 0.14f
                typeface = Typeface.DEFAULT_BOLD
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    rightMargin = dp(12)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val headline = TextView(ctx).apply {
                setTextColor(AndroidColor.parseColor("#F0FAE1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                maxLines = 2
            }
            val body = TextView(ctx).apply {
                setTextColor(AndroidColor.parseColor("#AEBF92"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                setPadding(0, dp(2), 0, 0)
            }
            textCol.addView(headline)
            textCol.addView(body)
            row.addView(icon)
            row.addView(textCol)

            val cta = Button(ctx).apply {
                setTextColor(AndroidColor.parseColor("#272E1B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                isAllCaps = false
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(AndroidColor.parseColor("#CCDBB2"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
                ).apply { topMargin = dp(12) }
                minWidth = dp(140)
            }

            card.addView(badge)
            card.addView(row)
            card.addView(cta)
            adView.addView(card)

            adView.headlineView = headline
            adView.bodyView = body
            adView.iconView = icon
            adView.callToActionView = cta
            adView
        },
        update = { adView ->
            (adView.headlineView as TextView).text = ad.headline
            (adView.bodyView as TextView).apply {
                text = ad.body ?: ""
                visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            (adView.iconView as ImageView).apply {
                val d = ad.icon?.drawable
                setImageDrawable(d)
                visibility = if (d == null) View.GONE else View.VISIBLE
            }
            (adView.callToActionView as Button).apply {
                text = ad.callToAction ?: "Learn more"
                visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            adView.setNativeAd(ad)
        }
    )
}
