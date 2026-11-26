package com.seatsense.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * On the hosted demo the backend also serves the built React app (Dockerfile). The app's pages are
 * client-side routes, so opening or refreshing /kiosk directly must hand back index.html.
 */
@Controller
public class SpaRoutes {

    /**
     * The React routes in frontend/src/App.tsx, apart from "/" which Spring serves as index.html already.
     * Keep in step with the mapping below (an annotation cannot read this array).
     */
    public static final String[] PAGES = {"/kiosk", "/scanner", "/portal", "/dashboard", "/cards", "/privacy", "/terms", "/display"};

    @GetMapping({"/kiosk", "/scanner", "/portal", "/dashboard", "/cards", "/privacy", "/terms", "/display"})
    public String page() {
        return "forward:/index.html";
    }
}
