package com.muhammaddaffa.nextgens.gui.helpers;

import com.muhammaddaffa.nextgens.objects.ActiveGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewPagination {

    private final Map<Integer, List<ActiveGenerator>> pageItems = new HashMap<>();

    public int totalPage = 1;
    public int currentPage = 1;

    public ViewPagination(List<ActiveGenerator> generators, List<Integer> slots) {

        int max = slots.size();
        if (generators.size() < max) {
            this.pageItems.put(this.totalPage, generators);
            return;
        }

        List<ActiveGenerator> pageGens = new ArrayList<>();

        for (int i = 0; i < generators.size(); i++) {
            // If the total added skins is over the max item amount
            // Put the skins into the map and "create a new page"
            if(i % max == 0 && i != 0){
                this.pageItems.put(this.totalPage, pageGens);
                pageGens = new ArrayList<>();
                this.totalPage++;
            }
            // If the leftover skins doesn't exceed the max amount
            if(i % max != 0 && i == (generators.size() - 1)){
                this.pageItems.put(this.totalPage, pageGens);
            }
            pageGens.add(generators.get(i));
        }
    }

    public boolean nextPage() {
        return this.pageItems.get(this.currentPage + 1) != null;
    }

    public boolean previousPage() {
        return this.pageItems.get(this.currentPage - 1) != null;
    }

    public List<ActiveGenerator> getItems(int page) {
        return this.pageItems.get(page);
    }

}
