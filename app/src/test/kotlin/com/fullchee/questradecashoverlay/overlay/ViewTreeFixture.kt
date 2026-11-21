package com.fullchee.questradecashoverlay.overlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.xml.parsers.DocumentBuilderFactory
import org.robolectric.Shadows.shadowOf
import org.w3c.dom.Element

/** Rebuilds a `uiautomator dump` as the node tree the accessibility service would see. */
fun viewTreeFixture(name: String): AccessibilityNodeInfo {
    val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/questrade/$name.xml"))
    val hierarchy = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
    return toNode(hierarchy.documentElement.childElements().single())
}

/** A one-node window, for screens that only need a package and a few labels. */
fun node(
    packageName: String = QUESTRADE_PACKAGE,
    viewId: String? = null,
    text: String? = null,
    contentDescription: String? = null,
    bounds: Rect = Rect(),
    children: List<AccessibilityNodeInfo> = emptyList()
): AccessibilityNodeInfo = AccessibilityNodeInfo().apply {
    this.packageName = packageName
    viewIdResourceName = viewId
    this.text = text
    this.contentDescription = contentDescription
    setBoundsInScreen(bounds)
    children.forEach { shadowOf(this).addChild(it) }
}

private fun toNode(element: Element): AccessibilityNodeInfo = node(
    packageName = element.getAttribute("package"),
    viewId = element.getAttribute("resource-id").ifEmpty { null },
    text = element.getAttribute("text").ifEmpty { null },
    contentDescription = element.getAttribute("content-desc").ifEmpty { null },
    bounds = parseBounds(element.getAttribute("bounds")),
    children = element.childElements().map(::toNode)
)

// uiautomator writes bounds as [left,top][right,bottom].
private fun parseBounds(bounds: String): Rect {
    val (left, top, right, bottom) = Regex("""-?\d+""").findAll(bounds).map {
        it.value.toInt()
    }.toList()
    return Rect(left, top, right, bottom)
}

private fun Element.childElements(): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
