package ohhtml.toc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.pmw.tinylog.Logger;

import com.github.template72.data.DataList;
import com.github.template72.data.DataMap;

import ohhtml.base.Escaper;

/**
 * Table of contents macro: generate TOC at begin of page, if getTocHeadingsLevels() or getTocSubpagesLevels() are greater 0.
 * 
 * Also generate invisible help keys links for editing help keys for headings (anchor linking).
 */
public class TocMacro {
    protected final TocMacroPage page;
    protected final String customer;
    protected final String lang;
    protected final String liStyle;
    private String toc = null;
    private IPage seite; // can be null
    private String helpKeysText = "Hilfe-Keys";
    
    public TocMacro(TocMacroPage page, String customer, String lang, String liStyle) {
        this.page = page;
        this.customer = customer;
        this.lang = lang;
        this.liStyle = liStyle;
    }

    public void setSeite(IPage seite) {
        this.seite = seite;
    }

    public String transform(String html) {
        toc = "";
        int headingslevels = page.getTocHeadingsLevels();
        int subpagesLevels = page.getTocSubpagesLevels();
        if (headingslevels == 0 && subpagesLevels == 0) {
            return html; // nothing to do
        }
        
        Document doc = Jsoup.parse(html);
        Elements headings = _getHeadings(doc);
        List<TocEntry> entries = new ArrayList<>();
        if (headingslevels > 0) {
            collectTocEntries(headingslevels, headings, entries);
        }
        
        final int nHeadings = entries.size();
        subpages2TocEntries(page.getSubpages(lang), entries, 1, subpagesLevels);
        
        if (!entries.isEmpty()) {
            toc = "<div class=\"toc\">" + makeTocHtml(entries, nHeadings) + "</div>";
        }
        return doc.html();
    }

    private void collectTocEntries(int headingslevels, Elements headings, List<TocEntry> entries) {
        TocEntry h2 = null, h3 = null, h4 = null, h5 = null;
        int lfd = 0;
        for (Element heading : headings) {
            if (ignoreHeading(heading, headingslevels)) {
                continue;
            }
            String title = heading.text();
            TocEntry entry = new TocEntry();
            entry.setTitle(title);
            entry.setId("#t" + ++lfd);
            heading.attr("id", entry.getId().substring("#".length())); // modify HTML
            if (seite != null) {
                List<String> helpKeys = seite.getHeadingHelpKeys(lang, title);
				heading.append(makeHeading(helpKeys, lfd));
            }
            
            int level = Integer.parseInt(heading.nodeName().substring(1, 2));
            if (level == 6 && h5 != null) {
                h5.getSubpages().add(entry);
            } else if (level == 5 && h4 != null) {
                h4.getSubpages().add(entry);
                h5 = entry;
            } else if (level == 4 && h3 != null) {
                h3.getSubpages().add(entry);
                h4 = entry;
            } else if (level == 3 && h2 != null) {
                h2.getSubpages().add(entry);
                h3 = entry;
            } else if (level == 2) {
                entries.add(entry);
                h2 = entry;
            } else {
                entries.add(entry);
            }
        }
    }
    
    protected String makeHeading(List<String> helpKeys, int lfd) {
		String css;
		String ext = "";
		if (helpKeys.isEmpty()) {
			css = "edithk0"; // invisible link
		} else {
			css = "edithk1"; // visible link
			ext = ": " + formatHeadingHelpKeys(helpKeys);
		}
		String link = getHeadingHelpKeysLink(lfd);
    	return "<a href=\"" + link + "\" class=\"" + css + "\">" + Escaper.esc(getHelpKeysText() + ext) + "</a>";
    }
    
    protected String formatHeadingHelpKeys(List<String> helpKeys) {
    	return helpKeys.stream().collect(Collectors.joining(", "));
    }
    
    protected String getHeadingHelpKeysLink(int lfd) {
    	return page.getId() + "/help-keys/" + lang + "/" + lfd;
    }
    
    public static boolean ignoreHeading(Element heading, IPage seite) {
        return ignoreHeading(heading, seite.getTocHeadingsLevels());
    }

    private static boolean ignoreHeading(Element heading, int headingslevels) {
        int level = Integer.parseInt(heading.nodeName().substring(1, 2));
        return headingslevels < level - 1; // true=continue
    }

    private void subpages2TocEntries(List<TocMacroPage> seiten, List<TocEntry> entries, int level, int maxLevel) {
        if (level > maxLevel) {
            return;
        }
        for (TocMacroPage seite : seiten) {
            if (seite.isVisible(customer, lang)) {
                TocEntry entry = new TocEntry();
                entry.setId(seite.getId());
                entry.setTitle(seite.getTitle(lang));
                entries.add(entry);
                subpages2TocEntries(seite.getSubpages(lang), entry.getSubpages(), level + 1, maxLevel); // recursive
            }
        }
    }

    private String makeTocHtml(List<TocEntry> entries, int nHeadings) {
        String ret = "";
        int n = entries.size();
        if (n > 0) {
            ret = "\n<ul class=\"toc\">";
        }
        for (int i = 0; i < n; i++) {
            TocEntry entry = entries.get(i);
            String cls = "";
            if (i >= nHeadings) {
                cls = " class=\"subpage\"";
                nHeadings = Integer.MAX_VALUE;
            }
            ret += "<li" + cls + liStyle + ">" //
                    + "<a href=\"" + entry.getId() + "\">" + entry.getTitle() + "</a>" //
                    + makeTocHtml(entry.getSubpages(), Integer.MAX_VALUE) // recursive 
                    + "</li>";
        }
        if (n > 0) {
            ret += "</ul>\n";
        }
        return ret;
    }

    /**
     * Must be called after transform().
     * @return TOC HTML
     */
    public String getTOC() {
        if (toc == null) {
            throw new RuntimeException("Call transform() before getTOC()!");
        }
        return toc;
    }

    public Elements getHeadings() {
        Document doc = Jsoup.parse(seite.getContent(lang));
        return _getHeadings(doc);
    }
    
    public static Elements _getHeadings(Document doc) {
        return doc.select("h2,h3,h4,h5,h6");
    }

    /**
     * call setSeite() before
     * @param p -
     * @return greater 0 if there are errors
     */
    public int fillHkhErrors(DataMap p) {
        DataList list = p.list("errors");
        if (seite.getHkh() != null) {
            Elements headings = getHeadings();
            for (Iterator<HelpKeysForHeading> iterator = seite.getHkh().iterator(); iterator.hasNext();) {
                HelpKeysForHeading i = iterator.next();
                if (i.getLanguage().equals(lang) && !exist(i, headings)) { // There are help keys for a non-existing heading.
                    DataMap map = list.add();
                    map.put("headingTitle", Escaper.esc(i.getHeading()));
                    map.put("helpKeys", Escaper.esc(i.getHelpKeys().stream().collect(Collectors.joining(", "))));
                }
            }
        }
        int n = list.size();
        p.put("hasErrors", n > 0);
        return n;
    }
    
    /**
     * call setSeite() before
     * @return true if data has been changed and must be saved
     */
    public boolean cleanupHkhErrors() {
        boolean dirty = false;
        if (seite.getHkh() != null) {
            Elements headings = getHeadings();
            for (Iterator<HelpKeysForHeading> iterator = seite.getHkh().iterator(); iterator.hasNext();) {
                HelpKeysForHeading i = iterator.next();
                if (i.getLanguage().equals(lang) && !exist(i, headings)) { // There are help keys for a non-existing heading.
                    Logger.info("delete HKH (" + i.getLanguage() + "): " + i.getHeading() + "=" + i.getHelpKeys());
                    iterator.remove();
                    dirty = true;
                }
            }
            if (seite.getHkh().isEmpty()) {
                seite.clearHkh();
                dirty = true;
            }
        }
        return dirty;
    }
    
    private boolean exist(HelpKeysForHeading i, Elements headings) {
        for (Element heading : headings) {
            if (!ignoreHeading(heading, seite) && i.getHeading().equals(heading.text())) {
                return true;
            }
        }
        return false;
    }

	public String getHelpKeysText() {
		return helpKeysText;
	}

	public void setHelpKeysText(String helpKeysText) {
		this.helpKeysText = helpKeysText;
	}
}
