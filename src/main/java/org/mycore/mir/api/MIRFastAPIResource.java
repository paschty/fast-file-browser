/*
 *  This file is part of ***  M y C o R e  ***
 *  See http://www.mycore.de/ for details.
 *
 *  This program is free software; you can use it, redistribute it
 *  and / or modify it under the terms of the GNU General Public License
 *  (GPL) as published by the Free Software Foundation; either version 2
 *  of the License or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program, in a file called gpl.txt or license.txt.
 *  If not, write to the Free Software Foundation Inc.,
 *  59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 */

/*
 *  This file is part of ***  M y C o R e  ***
 *  See http://www.mycore.de/ for details.
 *
 *  This program is free software; you can use it, redistribute it
 *  and / or modify it under the terms of the GNU General Public License
 *  (GPL) as published by the Free Software Foundation; either version 2
 *  of the License or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program, in a file called gpl.txt or license.txt.
 *  If not, write to the Free Software Foundation Inc.,
 *  59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 */

package org.mycore.mir.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.backend.hibernate.tables.MCRFSNODES;
import org.mycore.backend.hibernate.tables.MCRFSNODES_;
import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.MCRException;
import org.mycore.frontend.MCRFrontendUtil;

import com.google.gson.Gson;

@Path("api/fast/derivate")
public class MIRFastAPIResource {

    private static Logger LOGGER = LogManager.getLogger();

    @GET
    @Path("/{derivateID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDerivateContent(@PathParam("derivateID") String derivateID) {

        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<MCRFSNODES> query = cb.createQuery(MCRFSNODES.class);
        Root<MCRFSNODES> nodes = query.from(MCRFSNODES.class);
        try {
            List<MCRFSNODES> resultList = em.createQuery(query
                .where(
                    cb.equal(nodes.get(MCRFSNODES_.owner), derivateID)))
                .getResultList();

            MCRFSNODES rootNode = resultList.parallelStream()
                .filter(node -> node.getName() != null && node.getName().equals(derivateID))
                .findFirst()
                .orElseThrow(() -> new MCRException("Could not find " + derivateID.toString()));

            MIRFolderJson tree = buildTree(rootNode, resultList, derivateID, null);
            String json = new Gson().toJson(tree);
            return Response.ok().entity(json).build();
        } catch (NoResultException e) {
            LOGGER.warn("There is no fsnode with OWNER = " + derivateID);
            return null;
        }

    }

    public MIRFolderJson buildTree(MCRFSNODES currentNode, List<MCRFSNODES> nodes, String derivateID,
        MIRFolderJson parent) {
        MIRFolderJson treeElement;

        if (derivateID != null) {
            treeElement = new MIRRootFolder();
            ((MIRRootFolder) treeElement).mycorederivate = derivateID;
        } else {
            treeElement = new MIRFolderJson();
        }

        treeElement.parent = parent;
        enrichElement(treeElement, currentNode);

        treeElement.children = Collections.synchronizedList(new ArrayList<MIRFileJson>());
        nodes.stream().filter(n -> {
            return n.getPid() != null && n.getPid().equals(currentNode.getId());
        }).forEach(childNode -> {
            MIRFileJson child;
            if (childNode.getType().equals("D")) {
                child = buildTree(childNode, nodes, null, treeElement);
            } else {
                child = new MIRFileJson();
                child.parent = treeElement;
                enrichElement(child, childNode);
            }
            treeElement.children.add(child);
        });

        return treeElement;
    }

    private void enrichElement(MIRFileJson node, MCRFSNODES currentNode) {
        node.type = currentNode.getType().equals("D") ? "directory" : "file";
        node.name = currentNode.getName();
        node.path = buildPath(node);
        node.parentPath = node.parent == null ? "/" : node.parent.path;
        node.extension = currentNode.getFctid();
        node.size = currentNode.getSize();
        node.href = MCRFrontendUtil.getBaseURL() + "servlets/MCRFileNodeServlet/" + currentNode.getOwner() + node.path;
    }

    private String buildPath(MIRFileJson file) {
        String name = file.parent == null ? "" : buildPath(file.parent) + file.name;
        return name + (file.type.equals("directory") ? "/" : "");
    }

    class MIRRootFolder extends MIRFolderJson {
        public String mycorederivate;
    }

    class MIRFolderJson extends MIRFileJson {
        public List<MIRFileJson> children;
    }

    class MIRFileJson {

        public transient MIRFolderJson parent;

        public String type;

        public String name;

        public String path;

        public String parentPath;

        public String extension;

        public long size;

        public String href;
    }

}
