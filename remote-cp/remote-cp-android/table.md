Here is the visual wiring diagram mapping exactly how to wire your breadboard, the empty **RV1** pads on your buck converter, and your **MCP4725 DAC** to achieve safe 12\text{V} to 24\text{V} control.
### Physical Wire Connections
Follow this pin-by-pin layout to bridge your breadboard directly to the empty potentiometer holes on the buck converter:

| Component / Node | Connects To... | Purpose |
| :--- | :--- | :--- |
| **RV1 Left Pad (0\text{V})** | Tie directly to **Breadboard GND Rail** | Establishes the common ground reference. |
| **RV1 Middle Pad (24\text{V})** | Connect to an empty breadboard row (**Row A**) | Delivers the raw high-voltage output sense line to the breadboard. |
| **Resistor 1 (10\text{k}\Omega)** | Bridge between **Row A** and a new row (**Row B**) | Drops the high voltage down to safely isolate your digital gear. |
| **Resistor 2 (1\text{k}\Omega)** | Bridge between **Row B** and **Breadboard GND Rail** | Completes the standard divider baseline. |
| **RV1 Right Pad** | Tie a jumper wire directly to **Row B** | Sends the modified low-voltage feedback signal back to the control IC. |
| **Resistor 3 (4.7\text{k}\Omega)** | Bridge between **Row B** and your **MCP4725 OUT** | Injects the analog control voltage into the feedback loop. |

### Verification Checklist Before Power-On
Before flipping the main switch, trace the paths on your breadboard to ensure everything matches up:
 * **Common Ground Link:** Double-check that a wire runs from the Arduino/DAC ground network to the **RV1 Left Pad**. If grounds aren't tied together, the circuit will fluctuate randomly.
 * **The Fusion Node (Row B):** Verify that exactly four things meet inside this specific breadboard column:
   1. One leg of the 10\text{k}\Omega resistor.
   2. One leg of the 1\text{k}\Omega resistor.
   3. One leg of the 4.7\text{k}\Omega injection resistor.
   4. The return wire traveling back to the **RV1 Right Pad**.
Once everything is aligned, set your code to output a 5\text{V} DAC value first to boot the fan safely at its lowest speed (\sim12\text{V}), then gradually drop the DAC output toward 0\text{V} to smoothly accelerate the fan to full power!