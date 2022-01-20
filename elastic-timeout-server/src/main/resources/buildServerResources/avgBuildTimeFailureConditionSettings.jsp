<%@ include file="/include-internal.jsp" %>

<l:settingsGroup title="Conditions">
    <tr>
        <th>Builds must be</th>
        <td>
            <props:radioButtonProperty name="status_radio" value="Successful"/>Successful<br/>
            <props:radioButtonProperty name="status_radio" value="Any"/>Any<br/>
        </td>
    </tr>
    <tr>
        <th>How many taking into consideration?</th>
        <td>
            <props:textProperty name="build_count"/>
            <span class="smallNote">If there are not enough builds in history the condition will be ignored.</span>
        </td>
    </tr>
    <tr>
        <th>Exceeds by:</th>
        <td>
            <props:textProperty name="exceed_value"/>
            <props:selectProperty name="exceed_unit">
                <props:option value="seconds">
                    <c:out value="seconds"/>
                </props:option>
                <props:option value="percent" selected="true">
                    <c:out value="percent"/>
                </props:option>
            </props:selectProperty>
        </td>
    </tr>
    <!--<tr>
        <th>Stop build:</th>
        <td>
            <props:checkboxProperty name="stop_build" uncheckedValue="false"/>
            <span class="smallNote">Immediately stop the build if it fails due to the condition.</span>
        </td>
    </tr>-->
</l:settingsGroup>