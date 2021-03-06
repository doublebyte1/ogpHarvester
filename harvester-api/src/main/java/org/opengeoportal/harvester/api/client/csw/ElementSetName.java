package org.opengeoportal.harvester.api.client.csw;

import org.opengeoportal.harvester.api.client.csw.exception.InvalidParameterValueEx;

public enum ElementSetName {
    BRIEF("brief"), SUMMARY("summary"), FULL("full");

    private String setName;

    private ElementSetName(String setName) {
        this.setName = setName;
    }

    public String toString() {
        return setName;
    }

    /**
     * Parse a string containing the element set name value.
     *
     * @param setName
     * @return
     * @throws InvalidParameterValueEx
     */
    public static ElementSetName parse(String setName) throws InvalidParameterValueEx {
        if (setName == null)
            return SUMMARY; // required by CSW 2.0.2
        if (setName.equals(BRIEF  .toString()))	return BRIEF;
        if (setName.equals(SUMMARY.toString())) 	return SUMMARY;
        if (setName.equals(FULL   .toString()))	return FULL;

        throw new InvalidParameterValueEx("elementSetName", setName);
    }
}