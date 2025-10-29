package co.edu.uniquindio.proyectoAlgoritmos.util;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.Select;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Slf4j
@Service
public class AutomationUtils {

    private static final Random RND = new Random();

    public void humanDelay() { sleep(1000, 2200); }
    public void shortDelay() { sleep(500, 900); }
    public void downloadDelay() { sleep(2500, 3000); }

    private void sleep(int base, int jitter) {
        try { Thread.sleep(base + RND.nextInt(jitter)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public WebElement waitClickable(WebDriver d, By by, int sec) {
        return new WebDriverWait(d, Duration.ofSeconds(sec)).until(ExpectedConditions.elementToBeClickable(by));
    }

    public WebElement waitVisible(WebDriver d, By by, int sec) {
        return new WebDriverWait(d, Duration.ofSeconds(sec)).until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public void clickJS(WebDriver d, WebElement el) {
        ((JavascriptExecutor) d).executeScript("arguments[0].click();", el);
    }

    public void scrollIntoView(WebDriver d, WebElement el) {
        ((JavascriptExecutor) d).executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", el);
    }

    public void scrollBottom(WebDriver d) {
        ((JavascriptExecutor) d).executeScript("window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});");
        humanDelay();
    }

    public boolean clickAny(WebDriver d, int timeoutSec, By... locators) {
        for (By by : locators) {
            try {
                WebElement el = new WebDriverWait(d, Duration.ofSeconds(timeoutSec))
                        .until(ExpectedConditions.elementToBeClickable(by));
                scrollIntoView(d, el);
                try { el.click(); } catch (Exception e) { clickJS(d, el); }
                shortDelay();
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public WebElement findAnyVisible(WebDriver d, int timeoutSec, By... locators) {
        for (By by : locators) {
            try {
                WebElement el = new WebDriverWait(d, Duration.ofSeconds(timeoutSec))
                        .until(ExpectedConditions.visibilityOfElementLocated(by));
                return el;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public boolean setPerPage100(WebDriver d, By dropdown, By option100, By selectControl) {
        // soporta dropdown + opción 100 o select nativo
        try {
            if (dropdown != null) {
                WebElement dd = waitClickable(d, dropdown, 12);
                dd.click();
                shortDelay();
            }
            if (option100 != null) {
                WebElement op = waitClickable(d, option100, 8);
                scrollIntoView(d, op);
                try { op.click(); } catch (Exception e) { clickJS(d, op); }
                humanDelay();
                return true;
            }
        } catch (Exception ignored) {}
        if (selectControl != null) {
            try {
                WebElement sel = waitVisible(d, selectControl, 8);
                ((JavascriptExecutor) d).executeScript("arguments[0].value='100'; arguments[0].dispatchEvent(new Event('change'));", sel);
                humanDelay();
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // Nuevo: seleccionar formato por valor o texto, soportando <select> nativo y selectores personalizados
    public boolean setSelectByValueOrText(WebDriver d, WebElement selectEl, String[] candidates) {
        try {
            if (selectEl == null || candidates == null || candidates.length == 0) return false;
            String tag = "";
            try { tag = selectEl.getTagName(); } catch (Exception ignore) {}
            if (tag != null && tag.equalsIgnoreCase("select")) {
                try {
                    Select sel = new Select(selectEl);
                    for (String c : candidates) { try { sel.selectByValue(c); return true; } catch (Exception ignored) {} }
                    for (String c : candidates) { try { sel.selectByVisibleText(c); return true; } catch (Exception ignored) {} }
                } catch (Exception ignore) {}
                // Fallback JS
                for (String c : candidates) {
                    try {
                        ((JavascriptExecutor) d).executeScript(
                                "for(const o of arguments[0].options){if(o.text.trim().toLowerCase()==arguments[1]||o.value.trim().toLowerCase()==arguments[1]){o.selected=true;arguments[0].dispatchEvent(new Event('change',{bubbles:true}));return;}}",
                                selectEl, c.toLowerCase());
                        shortDelay();
                        return true;
                    } catch (Exception ignored) {}
                }
            } else {
                // Select personalizado: abrir y elegir opción por texto
                try { selectEl.click(); } catch (Exception e) { clickJS(d, selectEl); }
                shortDelay();
                for (String c : candidates) {
                    String upper = c.toUpperCase();
                    By[] opts = new By[] {
                            By.xpath("//li[contains(@class,'select2-results__option') and translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')='" + upper + "']"),
                            By.xpath("//option[translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')='" + upper + "']"),
                            By.xpath("//div[contains(@class,'option') and translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')='" + upper + "']"),
                            By.xpath("//label[span[translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')='" + upper + "']]/input")
                    };
                    for (By by : opts) {
                        try {
                            WebElement op = waitClickable(d, by, 5);
                            scrollIntoView(d, op);
                            try { op.click(); } catch (Exception e) { clickJS(d, op); }
                            shortDelay();
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignore) {}
        return false;
    }
}
