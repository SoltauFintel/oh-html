package ohhtml.downloads;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import github.soltaufintel.amalia.web.action.Escaper;

/**
 * Insert download areas into HTML
 */
public class DownloadsHTML {
    public static final String ALL_FILES_DE = "alle-Dateien";
    public static final String ALL_FILES_EN = "all-files";

    public String downloads(String html, String customer, String lang) {
        return downloads(html, customer, lang, c -> getDownloads(c));
    }
    
    public interface GetDownloads {
        List<Download> getDownloads(String customer);
    }
    
    public String downloads(String html, String customer, String lang, GetDownloads supplier) {
        if (html == null || !html.contains("${" + getWord())) {
            return html;
        }
        List<Download> downloads = supplier.getDownloads(customer);
        if (GetAttachments.debug) {
        for (Download d : downloads) {
            System.out.println("- Download: getFileInWorkspace=" +d.getFileInWorkspace() + " | filename="+d.getFilename() + " | name="+d.getName()+" | keys: "+d.getKeys());
        }}
        List<DownloadOccurrence> oc = findOccurrences(html);
        if (GetAttachments.debug) {
            System.out.println("findOccurrences: " + oc.size() + " | downloads: " + downloads.size());
            for (DownloadOccurrence c : oc) {
                System.out.println("- DownloadOccurrence: " + c.getKey() + " | " + c.getText() + " | " + c.isOne());
            }
        }
        for (DownloadOccurrence c : oc) {
            List<Download> filteredDownloads;
            if (c.getKey().isEmpty()) { // empty key means all files
                if (GetAttachments.debug) {
                    System.out.println("Ast 1: alle");
                }
                filteredDownloads = downloads;
            } else {
                if (GetAttachments.debug) {
                    System.out.println("Ast 2: Selektion: " + c.getKey());
                }
                filteredDownloads = downloads.stream().filter(d -> d.getKeys().contains(c.getKey())).collect(Collectors.toList());
                if (GetAttachments.debug && filteredDownloads.isEmpty()) {
                    System.out.println("filteredDownloads is empty -----> filteredDownloads = downloads");
                    filteredDownloads = downloads;
                }                
            }
            if (GetAttachments.debug) {
                System.out.println("filteredDownloads size: " + filteredDownloads.size());
            }
            html = html.replace(c.getText(), makeDownloadComponent(c, filteredDownloads, lang));
        }
        return html;
    }

    public static List<Download> getDownloads(String customer) {
        File downloadsFile = new File("downloads/downloads.txt");
        if (!downloadsFile.isFile()) {
            return new ArrayList<>();
        }
        Downloads ds = new Downloads();
        List<Download> dl = ds.parseDownloads(downloadsFile);
        ds.resolve(dl, new File("downloads"));
        return dl.stream().filter(d -> d.getCustomers().contains(customer)).collect(Collectors.toList());
    }

    /** public for test */
    public List<DownloadOccurrence> findOccurrences(String html) {
        List<DownloadOccurrence> ret = new ArrayList<>();
        int o = html.indexOf("${" + getWord());
        while (o >= 0) {
            int oo = html.indexOf("}", o);
            if (oo >= 0) {
                createOccurrence(html.substring(o, oo + "}".length()), ret);
            }

            o = html.indexOf("${" + getWord(), o + ("${" + getWord()).length());
        }
        return ret;
    }

    private void createOccurrence(String text, List<DownloadOccurrence> ret) {
        DownloadOccurrence c = new DownloadOccurrence();
        c.setText(text);
        c.setOne(!text.startsWith("${" + getWord() + "s"));
        int o = text.indexOf("=");
        if (o >= 0) {
            c.setKey(text.substring(o + 1, text.length() - "}".length()).trim());
        }
        if (!ret.stream().anyMatch(i -> i.equals(c))) {
            ret.add(c);
        }
    }

    private String makeDownloadComponent(DownloadOccurrence c, List<Download> downloads, String lang) {
        List<File> files = new ArrayList<>();
        for (Download d : downloads) {
            files.addAll(d.getFiles());
        }
        // TODO Wie die files sortieren?
        if (files.isEmpty()) {
            return noDownloads(lang);
        }
        String key = c.getKey();
        if (key.isEmpty()) {
            key = "de".equals(lang) ? ALL_FILES_DE : ALL_FILES_EN;
        }
        if (c.isOne()) { // one inline download link
            if (files.size() == 1) {
                return "<a href=\"/" + getWord() + "?file=" + Escaper.urlEncode(files.get(0).toString(), "") + "\">" + files.get(0).getName() + "</a>";
            } else {
                return "<a href=\"/" + getWord() + "?zip=" + Escaper.urlEncode(key, "") + ".zip\">" + key + ".zip (" + files.size() + ")</a>";
            }
        } else { // list of download links
            String ret = "";
            for (File file : files) {
                ret += "<li><a href=\"/" + getWord() + "?file=" + Escaper.urlEncode(file.toString(), "") + "\">" + file.getName() + "</a></li>\n";
            }
            return "<ul>" + ret + "</ul>";
        }
    }
    
    // plural is getWord() + "s"
    protected String getWord() {
        return "download";
    }
    
    protected String noDownloads(String lang) {
        return "de".equals(lang) ? "[keine Downloads]" : "[no downloads]";
    }
}
