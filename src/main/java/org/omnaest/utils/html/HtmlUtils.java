package org.omnaest.utils.html;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.omnaest.utils.ListUtils;
import org.omnaest.utils.StreamUtils;
import org.omnaest.utils.rest.client.RestClient;
import org.omnaest.utils.rest.client.RestClient.MediaType;

public class HtmlUtils
{

    public static interface ElementFinder<R>
    {
        Optional<HtmlElement> findById(String id);

        Stream<HtmlElement> findByTag(String tag);

        R visit(Visitor visitor);
    }

    public static interface HtmlElement extends ElementFinder<HtmlElement>
    {
        public String asText();

        public Optional<HtmlAnker> asAnker();

        public Element asRawElement();

        public Optional<HtmlElement> getParent();

        public Stream<HtmlElement> getParents();
    }

    public static interface HtmlAnker extends HtmlElement
    {
        public String getHref();
    }

    public static interface HtmlDocument extends ElementFinder<HtmlDocument>
    {
        public Document get();

        public String asText();

    }

    public static interface HtmlDocumentLoader
    {
        public HtmlDocument fromUrl(String url);

        public HtmlDocument from(String html);

        public HtmlDocument from(InputStream inputStream) throws IOException;

        public HtmlDocument from(File file) throws IOException;

        public HtmlDocumentLoader usingLocalCache();
    }

    public static interface HtmlElements extends Iterable<HtmlElement>
    {
        List<HtmlElement> getElements();

        Stream<HtmlElement> stream();
    }

    private static class HtmlElementsImpl implements HtmlElements
    {
        private List<Element> parents;

        public HtmlElementsImpl(List<Element> parents)
        {
            this.parents = ListUtils.inverse(parents);
        }

        @Override
        public List<HtmlElement> getElements()
        {
            return this.stream()
                       .collect(Collectors.toList());
        }

        @Override
        public Stream<HtmlElement> stream()
        {
            return this.parents.stream()
                               .map(e -> new HtmlElementWrapper(e));
        }

        @Override
        public Iterator<HtmlElement> iterator()
        {
            return this.stream()
                       .iterator();
        }
    }

    public static interface Visitor extends BiPredicate<HtmlElement, HtmlElements>
    {
    }

    private static class Traverser
    {

        private Element element;

        private Traverser(Element element)
        {
            this.element = element;
        }

        public Traverser visit(Visitor visitor)
        {
            return this.visit(visitor, Collections.emptyList(), this.element);
        }

        private Traverser visit(Visitor visitor, List<Element> parents, Element element)
        {
            if (element != null)
            {
                boolean proceed = visitor.test(new HtmlElementWrapper(element), new HtmlElementsImpl(parents));
                if (proceed)
                {
                    element.children()
                           .forEach(child -> this.visit(visitor, ListUtils.addToNew(parents, element), child));
                }
            }
            return this;
        }

        public static Traverser of(Element element)
        {
            return new Traverser(element);
        }

        @Override
        public String toString()
        {
            return "Traverser [element=" + this.element + "]";
        }

    }

    private HtmlUtils()
    {
        super();
    }

    public static HtmlDocumentLoader load()
    {
        return new HtmlDocumentLoader()
        {
            private RestClient restClient = RestClient.newStringRestClient();

            @Override
            public HtmlDocumentLoader usingLocalCache()
            {
                this.restClient = this.restClient.withLocalCache("rest-calls");
                return this;
            }

            @Override
            public HtmlDocument fromUrl(String url)
            {
                String html = this.restClient.withAcceptMediaType(MediaType.TEXT_HTML)
                                             .request()
                                             .toUrl(url)
                                             .get(String.class);
                return this.from(html);
            }

            @Override
            public HtmlDocument from(String html)
            {
                Document document = Jsoup.parse(html);
                return new HtmlDocument()
                {
                    @Override
                    public Document get()
                    {
                        return document;
                    }

                    @Override
                    public String asText()
                    {
                        return document.text();
                    }

                    @Override
                    public Optional<HtmlElement> findById(String id)
                    {
                        return this.wrapElement(document.getElementById(id));
                    }

                    private Optional<HtmlElement> wrapElement(Element element)
                    {
                        return Optional.ofNullable(element)
                                       .map(e -> new HtmlElementWrapper(e));
                    }

                    @Override
                    public Stream<HtmlElement> findByTag(String tag)
                    {
                        return document.getElementsByTag(tag)
                                       .stream()
                                       .map(element -> this.wrapElement(element)
                                                           .get());
                    }

                    @Override
                    public HtmlDocument visit(Visitor visitor)
                    {
                        new Traverser(document).visit(visitor);
                        return this;
                    }
                };
            }

            @Override
            public HtmlDocument from(InputStream inputStream) throws IOException
            {
                return this.from(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
            }

            @Override
            public HtmlDocument from(File file) throws IOException
            {
                return this.from(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            }
        };
    }

    private static class HtmlElementDelegate extends AbstractHtmlElement
    {
        protected HtmlElement htmlElement;

        public HtmlElementDelegate(HtmlElement htmlElement)
        {
            super();
            this.htmlElement = htmlElement;
        }

        @Override
        public Optional<HtmlElement> findById(String id)
        {
            return this.htmlElement.findById(id);
        }

        @Override
        public Stream<HtmlElement> findByTag(String tag)
        {
            return this.htmlElement.findByTag(tag);
        }

        @Override
        public String asText()
        {
            return this.htmlElement.asText();
        }

        @Override
        public Optional<HtmlAnker> asAnker()
        {
            return this.htmlElement.asAnker();
        }

        @Override
        public Element asRawElement()
        {
            return this.htmlElement.asRawElement();
        }

        @Override
        public HtmlElement visit(Visitor visitor)
        {
            return this.htmlElement.visit(visitor);
        }

        @Override
        public Optional<HtmlElement> getParent()
        {
            return this.htmlElement.getParent();
        }

        @Override
        public Stream<HtmlElement> getParents()
        {
            return this.htmlElement.getParents();
        }

    }

    private static class HtmlAnkerImpl extends HtmlElementDelegate implements HtmlAnker
    {
        public HtmlAnkerImpl(HtmlElement htmlElement)
        {
            super(htmlElement);
        }

        @Override
        public String getHref()
        {
            return this.htmlElement.asRawElement()
                                   .attr("href");
        }
    }

    private static abstract class AbstractHtmlElement implements HtmlElement
    {

        @Override
        public int hashCode()
        {
            return this.asRawElement()
                       .hashCode();
        }

        @Override
        public Stream<HtmlElement> getParents()
        {
            return StreamUtils.generate()
                              .recursive(this, element -> element.getParent()
                                                                 .orElse(null));
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof HtmlElement))
            {
                return false;
            }
            return this.asRawElement()
                       .equals(((HtmlElement) obj).asRawElement());
        }

        @Override
        public String toString()
        {
            return this.asRawElement()
                       .toString();
        }

    }

    private static class HtmlElementWrapper extends AbstractHtmlElement
    {
        private final Element element;

        private HtmlElementWrapper(Element element)
        {
            this.element = element;
        }

        @Override
        public String asText()
        {
            return this.element.text();
        }

        @Override
        public Optional<HtmlElement> getParent()
        {
            return Optional.ofNullable(this.element.parent())
                           .map(e -> new HtmlElementWrapper(e));
        }

        @Override
        public Optional<HtmlElement> findById(String id)
        {
            return this.wrapElement(this.element.getElementById(id));
        }

        private Optional<HtmlElement> wrapElement(Element element)
        {
            return Optional.ofNullable(element)
                           .map(e -> new HtmlElementWrapper(e));
        }

        @Override
        public Stream<HtmlElement> findByTag(String tag)
        {
            return this.element.getElementsByTag(tag)
                               .stream()
                               .map(element -> this.wrapElement(element)
                                                   .get());

        }

        @Override
        public Element asRawElement()
        {
            return this.element;
        }

        @Override
        public Optional<HtmlAnker> asAnker()
        {
            return Optional.ofNullable(this.element)
                           .filter(e -> e.tagName() == "a")
                           .map(e -> new HtmlAnkerImpl(this));
        }

        @Override
        public HtmlElement visit(Visitor visitor)
        {
            Traverser.of(this.element)
                     .visit(visitor);
            return this;
        }

    }
}
