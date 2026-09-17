package com.thenetworkplan.networkplan.weather.service;

import com.thenetworkplan.networkplan.weather.dto.LegLvpDto;
import java.util.List;
import java.util.UUID;

/**
 * Le verdict de faible visibilite d'une etape, pour le bandeau du dossier de
 * vol.
 *
 * <p><b>Ce qui est evalue, et ce qui ne l'est pas.</b> Le moteur LVP de l'annexe
 * (prototype l. 73744-73900) confronte le RVR piste par piste aux minima
 * approuves de l'exploitant, verifie le balisage de la piste par palier, les
 * approbations SPA.LVO.100 et la fenetre TAF. Rien de tout cela n'existe encore
 * ici : ni table de minima approuves, ni releve de balisage, ni TAF decode.
 *
 * <p>Ce service rend donc ce qu'il peut demontrer, et dit « INSUFFICIENT DATA »
 * partout ailleurs — ce qui est precisement le verdict que l'annexe elle-meme
 * affiche quand une donnee obligatoire lui manque (l. 73763). Il ne rend jamais
 * « ABOVE_MINIMA » sur un minimum qu'il n'a pas : il se tait quand la
 * visibilite est franchement bonne, exactement comme le bandeau de l'annexe est
 * silencieux en GREEN.
 */
public interface LegLvpService {

    LegLvpDto assess(UUID tenantId, UUID legId);

    /**
     * Le meme verdict, sur des aerodromes plutot que sur une etape.
     *
     * <p>Le dossier de vol s'ouvre aussi sur un appareil immobilise, qui n'a pas
     * d'etape : l'annexe en fait un vol fictif dont le depart et l'arrivee sont
     * l'escale ou il se trouve (l. 22755). La question de la faible visibilite
     * garde tout son sens sur cette escale-la — on y remorque, on en repart en
     * convoyage, l'equipage doit y arriver.
     */
    LegLvpDto assessStations(UUID tenantId, List<String> icaoCodes);
}
