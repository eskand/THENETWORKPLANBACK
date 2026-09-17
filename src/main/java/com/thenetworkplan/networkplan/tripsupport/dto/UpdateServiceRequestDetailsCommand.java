package com.thenetworkplan.networkplan.tripsupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corriger la ligne elle-meme — son type de service, son fournisseur — et non
 * l'etat de la demande.
 *
 * <p>L'annexe laissait changer les deux a tout moment : sa ligne n'etait qu'un
 * objet du navigateur. Ici une demande envoyee est partie chez quelqu'un, et en
 * changer le destinataire apres coup ferait dire au dossier qu'elle est allee
 * la ou elle n'est pas allee. La correction n'est donc acceptee que tant que la
 * demande est un brouillon, ou apres un refus — les deux moments ou rien n'est
 * engage.
 */
public record UpdateServiceRequestDetailsCommand(
        @NotBlank String serviceType,
        @Size(max = 200) String supplierName,
        @Size(max = 500) String remark) {
}
